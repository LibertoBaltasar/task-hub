package org.taskhub.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.NotificationResponse
import kotlin.random.Random

/**
 * Talks directly to Firestore REST API — no Ktor server needed.
 *
 * Firestore REST API docs:
 *   https://firebase.google.com/docs/firestore/reference/rest
 *
 * Uses Firebase Anonymous Auth for write access.
 * The API key alone only allows reads — writes require a Bearer token.
 * Anonymous Auth requires zero user interaction (no Google Sign-In, no UI).
 */
class FirestoreRepository(
    private val projectId: String = "task-hub-62f98",
    private val apiKey: String = DEFAULT_API_KEY
) {
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    private val authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"

    // ── Auth state (in-memory, regenerated on app restart — fine for anonymous) ──
    @Volatile
    private var bearerToken: String? = null
    @Volatile
    private var tokenExpiry: Long = 0L  // epoch millis when token expires (minus safety margin)
    @Volatile
    private var cachedLocalId: String? = null  // anonymous user ID — persists across sessions via settings

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    // ────────────────────────────────────────────────────────
    //  Auth
    // ────────────────────────────────────────────────────────

    /**
     * Ensures we have a valid anonymous auth token, signing up anonymously if needed.
     * Called lazily on first request. Token is cached in memory and refreshed
     * when within 5 minutes of expiry.
     *
     * POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=API_KEY
     * Body: {"returnSecureToken":true}
     */
    private suspend fun ensureAuth() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (bearerToken != null && now < tokenExpiry) return

        val response: FirebaseAuthResponse = client.post("$authUrl?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(FirebaseAuthRequest(returnSecureToken = true))
        }.body()

        bearerToken = response.idToken
        cachedLocalId = response.localId
        // expiresIn is in seconds. Refresh 5 minutes before actual expiry.
        tokenExpiry = now + (response.expiresIn.toLong() * 1000) - 300_000
    }

    /** Returns the anonymous user's localId after auth. Null if not yet authenticated. */
    fun getLocalId(): String? = cachedLocalId

    /**
     * Adds Authorization header to a request builder if we already have a token.
     * Calls [ensureAuth] first so the token is always fresh.
     */
    private suspend fun HttpRequestBuilder.withAuth() {
        ensureAuth()
        bearerToken?.let { header("Authorization", "Bearer $it") }
    }

    // ────────────────────────────────────────────────────────
    //  Households
    // ────────────────────────────────────────────────────────

    /** Create a household (auto-generated doc ID). Requires auth (write). */
    suspend fun createHousehold(name: String): HouseholdResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val inviteCode = generateInviteCode()

        val fields = mapOf(
            "name" to FirestoreValue(stringValue = name),
            "inviteCode" to FirestoreValue(stringValue = inviteCode),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return HouseholdResponse(id, name, inviteCode, now, now)
    }

    /** Get a household by id. Falls back to API key if auth fails (read-only). */
    suspend fun getHousehold(id: String): HouseholdResponse {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$id") {
            // Try Bearer token first; API key fallback for reads
            tryAuthOrApiKey()
        }.body()

        return toHouseholdResponse(response)
    }

    /** Batch-fetch multiple households by their document IDs. */
    suspend fun getHouseholds(ids: List<String>): List<HouseholdResponse> {
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { id ->
            try {
                getHousehold(id)
            } catch (_: Exception) {
                null // stale ID from local store — skip
            }
        }
    }

    /** Delete a household document. Does NOT cascade-delete subcollections (members, tasks)
     *  — those become orphaned but harmless. Requires auth (write). */
    suspend fun deleteHousehold(householdId: String) {
        client.delete("$baseUrl/households/$householdId") {
            withAuth()
        }
    }

    /** Find a household by invite code (query). Falls back to API key for reads. */
    suspend fun joinHousehold(inviteCode: String): HouseholdResponse {
        val query = RunQueryRequest(
            structuredQuery = StructuredQuery(
                from = listOf(CollectionSelector("households")),
                where = Filter(
                    fieldFilter = FieldFilter(
                        field = FieldReference("inviteCode"),
                        op = "EQUAL",
                        value = FirestoreValue(stringValue = inviteCode)
                    )
                ),
                limit = 1
            )
        )

        val items: List<RunQueryResponseItem> = client.post("$baseUrl:runQuery") {
            tryAuthOrApiKey()
            contentType(ContentType.Application.Json)
            setBody(query)
        }.body()

        val doc = items.firstOrNull()?.document
            ?: throw IllegalStateException("Código de invitación inválido")

        return toHouseholdResponse(doc)
    }

    // ────────────────────────────────────────────────────────
    //  Household membership
    // ────────────────────────────────────────────────────────

    /**
     * Check if a user (localId) is already a member of the given household.
     */
    suspend fun isMember(householdId: String, localId: String): Boolean {
        return try {
            val members = getMembers(householdId)
            members.any { it.userId == localId }
        } catch (_: Exception) {
            false
        }
    }

    // ────────────────────────────────────────────────────────
    //  Members (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** List members of a household. Falls back to API key for reads. */
    suspend fun getMembers(householdId: String): List<MemberResponse> {
        val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/members") {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { toMemberResponse(it, householdId) }
    }

    /** Add a member to a household. Requires auth (write). */
    suspend fun createMember(
        householdId: String,
        displayName: String,
        role: String = "child",
        avatarUrl: String? = null,
        userId: String? = null
    ): MemberResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "householdId" to FirestoreValue(stringValue = householdId),
            "displayName" to FirestoreValue(stringValue = displayName),
            "role" to FirestoreValue(stringValue = role),
            "totalPoints" to FirestoreValue(integerValue = "0"),
            "joinedAt" to FirestoreValue(integerValue = now.toString()),
            "currentStreak" to FirestoreValue(integerValue = "0"),
            "bestStreak" to FirestoreValue(integerValue = "0"),
            "lastStreakDate" to FirestoreValue(integerValue = "0")
        )
        if (avatarUrl != null) {
            fields["avatarUrl"] = FirestoreValue(stringValue = avatarUrl)
        } else {
            fields["avatarUrl"] = FirestoreValue(nullValue = "NULL_VALUE")
        }
        if (userId != null) {
            fields["userId"] = FirestoreValue(stringValue = userId)
        } else {
            fields["userId"] = FirestoreValue(nullValue = "NULL_VALUE")
        }

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households/$householdId/members") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return MemberResponse(id, householdId, displayName, avatarUrl, role, 0, now,
            currentStreak = 0, bestStreak = 0, lastStreakDate = 0)
    }

    /** Remove (leave) a member — soft-delete by setting leftAt. Requires auth (write). */
    suspend fun deleteMember(householdId: String, memberId: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf(
            "leftAt" to FirestoreValue(integerValue = now.toString())
        )

        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "leftAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        return true
    }

    /** Update member streak fields. Requires auth (write). */
    suspend fun updateMemberStreak(
        householdId: String,
        memberId: String,
        currentStreak: Int,
        bestStreak: Int,
        lastStreakDate: Long
    ) {
        val fields = mapOf(
            "currentStreak" to FirestoreValue(integerValue = currentStreak.toString()),
            "bestStreak" to FirestoreValue(integerValue = bestStreak.toString()),
            "lastStreakDate" to FirestoreValue(integerValue = lastStreakDate.toString())
        )
        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "currentStreak,bestStreak,lastStreakDate")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Update member total points (add delta). Requires auth (write). */
    suspend fun addMemberPoints(
        householdId: String,
        memberId: String,
        delta: Int
    ) {
        // We need to read current points first, then update
        val members = getMembers(householdId)
        val member = members.find { it.id == memberId } ?: return
        val newTotal = member.totalPoints + delta

        val fields = mapOf(
            "totalPoints" to FirestoreValue(integerValue = newTotal.toString())
        )
        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "totalPoints")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Get member's unlocked achievement IDs. */
    suspend fun getMemberAchievements(householdId: String, memberId: String): Set<String> {
        return try {
            val response: FirestoreDocumentResponse = client.get(
                "$baseUrl/households/$householdId/members/$memberId/achievements/_meta"
            ) {
                tryAuthOrApiKey()
            }.body()
            val ids = response.fields["unlocked"]?.arrayValue?.values
                ?.mapNotNull { it.stringValue }
                ?: emptyList()
            ids.toSet()
        } catch (_: Exception) {
            emptySet() // No achievements doc yet
        }
    }

    /** Add an achievement ID to member's unlocked achievements. */
    suspend fun addMemberAchievement(householdId: String, memberId: String, achievementId: String) {
        // Use PATCH to create or update the meta document
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "unlocked" to FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = listOf(FirestoreValue(stringValue = achievementId))
                )
            ),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )
        // We need arrayUnion — use a different approach: read + write
        val existing = getMemberAchievements(householdId, memberId)
        val allUnlocked = existing + achievementId
        val updatedFields = mapOf(
            "unlocked" to FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = allUnlocked.map { FirestoreValue(stringValue = it) }
                )
            ),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )
        client.patch(
            "$baseUrl/households/$householdId/members/$memberId/achievements/_meta"
        ) {
            withAuth()
            // Create if not exists
            parameter("updateMask.fieldPaths", "unlocked,updatedAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(updatedFields))
        }
    }

    // ────────────────────────────────────────────────────────
    //  Tasks (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Create a task. Returns the created task. */
    suspend fun createTask(
        householdId: String,
        createdBy: String,
        title: String,
        description: String,
        points: Int,
        frequency: String,
        recurrenceDays: List<Int>,
        tags: List<String>,
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        dueDate: Long = 0,
        assignmentRotation: List<org.taskhub.network.models.AssignmentSlot> = emptyList()
    ): TaskResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "householdId" to FirestoreValue(stringValue = householdId),
            "createdBy" to FirestoreValue(stringValue = createdBy),
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "points" to FirestoreValue(integerValue = points.toString()),
            "frequency" to FirestoreValue(stringValue = frequency),
            "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        // Tags as array
        if (tags.isNotEmpty()) {
            fields["tags"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = tags.map { FirestoreValue(stringValue = it) }
                )
            )
        } else {
            fields["tags"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(values = emptyList())
            )
        }

        // Recurrence days as array
        if (recurrenceDays.isNotEmpty()) {
            fields["recurrenceDays"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = recurrenceDays.map { FirestoreValue(integerValue = it.toString()) }
                )
            )
        }

        // Penalty configuration
        if (penaltyMode != null) {
            fields["penaltyMode"] = FirestoreValue(stringValue = penaltyMode)
            fields["penaltyValue"] = FirestoreValue(integerValue = penaltyValue.toString())
            fields["penaltyInterval"] = FirestoreValue(stringValue = penaltyInterval)
            fields["penaltyMax"] = FirestoreValue(integerValue = penaltyMax.toString())
        }

        // Assignment rotation as array of maps
        if (assignmentRotation.isNotEmpty()) {
            fields["assignmentRotation"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = assignmentRotation.map { slot ->
                        FirestoreValue(
                            mapValue = FirestoreMapValue(
                                fields = mapOf(
                                    "dayOfWeek" to FirestoreValue(integerValue = slot.dayOfWeek.toString()),
                                    "memberId" to FirestoreValue(stringValue = slot.memberId)
                                )
                            )
                        )
                    }
                )
            )
        }

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households/$householdId/tasks") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return TaskResponse(
            id = id, householdId = householdId, createdBy = createdBy,
            title = title, description = description, points = points,
            frequency = frequency, recurrenceDays = recurrenceDays, tags = tags,
            penaltyMode = penaltyMode, penaltyValue = penaltyValue,
            penaltyInterval = penaltyInterval, penaltyMax = penaltyMax,
            dueDate = dueDate, lastCompletedDate = null,
            assignmentRotation = assignmentRotation,
            createdAt = now, updatedAt = now
        )
    }

    /** List all tasks for a household. */
    suspend fun getTasks(householdId: String): List<TaskResponse> {
        val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/tasks") {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { toTaskResponse(it, householdId) }
    }

    /** Mark a task as completed today. Sets lastCompletedDate, awards points, and records history. */
    suspend fun completeTask(
        householdId: String,
        taskId: String,
        memberId: String,
        taskPoints: Int
    ) {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Update lastCompletedDate on the task
        val fields = mapOf(
            "lastCompletedDate" to FirestoreValue(integerValue = now.toString())
        )

        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", "lastCompletedDate")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        // 2. Add points to the member
        addMemberPoints(householdId, memberId, taskPoints)

        // 3. Save task history record
        saveTaskHistory(
            householdId = householdId,
            taskId = taskId,
            memberId = memberId,
            points = taskPoints,
            completedAt = now,
            onTime = true
        )
    }

    /**
     * Revert a task completion — used by the undo feature.
     * Restores the previous lastCompletedDate on the task document.
     * Does NOT revert points/history (keeping it simple).
     */
    suspend fun revertTaskCompletion(
        householdId: String,
        taskId: String,
        previousLastCompletedDate: Long?
    ) {
        val value = if (previousLastCompletedDate != null) {
            FirestoreValue(integerValue = previousLastCompletedDate.toString())
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }
        val fields = mapOf("lastCompletedDate" to value)
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", "lastCompletedDate")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Save a task completion record to Firestore taskHistory subcollection. */
    suspend fun saveTaskHistory(
        householdId: String,
        taskId: String,
        memberId: String,
        points: Int,
        completedAt: Long,
        onTime: Boolean
    ) {
        val fields = mapOf(
            "taskId" to FirestoreValue(stringValue = taskId),
            "memberId" to FirestoreValue(stringValue = memberId),
            "points" to FirestoreValue(integerValue = points.toString()),
            "completedAt" to FirestoreValue(integerValue = completedAt.toString()),
            "onTime" to FirestoreValue(booleanValue = onTime)
        )

        client.post("$baseUrl/households/$householdId/taskHistory") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Get all task history records for a household. */
    suspend fun getTaskHistory(householdId: String): List<TaskHistoryResponse> {
        return try {
            val response: FirestoreListResponse = client.get(
                "$baseUrl/households/$householdId/taskHistory"
            ) {
                tryAuthOrApiKey()
            }.body()

            response.documents.map { doc ->
                val f = doc.fields
                TaskHistoryResponse(
                    id = extractDocId(doc.name),
                    taskId = f["taskId"]?.stringValue ?: "",
                    memberId = f["memberId"]?.stringValue ?: "",
                    points = f["points"]?.integerValue?.toIntOrNull() ?: 0,
                    completedAt = f["completedAt"]?.integerValue?.toLongOrNull() ?: 0L,
                    onTime = f["onTime"]?.booleanValue ?: true
                )
            }
        } catch (_: Exception) {
            emptyList() // No taskHistory subcollection yet
        }
    }

    /** Assign a task to one or more members with a due date. */
    suspend fun assignTask(
        householdId: String,
        taskId: String,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long,
        taskTitle: String = ""
    ): List<TaskAssignmentResponse> {
        val now = Clock.System.now().toEpochMilliseconds()
        val results = mutableListOf<TaskAssignmentResponse>()

        for (memberId in memberIds) {
            val fields = mutableMapOf<String, FirestoreValue>(
                "taskId" to FirestoreValue(stringValue = taskId),
                "memberId" to FirestoreValue(stringValue = memberId),
                "mandatory" to FirestoreValue(booleanValue = mandatory),
                "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
                "status" to FirestoreValue(stringValue = "assigned"),
                "assignedAt" to FirestoreValue(integerValue = now.toString())
            )

            val response: FirestoreDocumentResponse = client.post(
                "$baseUrl/households/$householdId/tasks/$taskId/assignments"
            ) {
                withAuth()
                contentType(ContentType.Application.Json)
                setBody(FirestoreDocument(fields))
            }.body()

            val id = extractDocId(response.name)
            results.add(TaskAssignmentResponse(
                id = id, taskId = taskId, memberId = memberId,
                mandatory = mandatory, dueDate = dueDate, status = "assigned",
                assignedAt = now
            ))

            // Create notification for the assigned member
            createNotification(
                householdId = householdId,
                memberId = memberId,
                taskId = taskId,
                title = "\uD83D\uDCCB Tarea asignada",
                message = if (taskTitle.isNotEmpty()) "Se te ha asignado: $taskTitle" else "Se te ha asignado una nueva tarea"
            )
        }

        return results
    }

    /** Get all assignments for a specific task. */
    suspend fun getAssignments(householdId: String, taskId: String): List<TaskAssignmentResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { toTaskAssignmentResponse(it, taskId) }
    }

    /** Get all assignments across all tasks for a household. */
    suspend fun getAllAssignments(householdId: String): List<TaskAssignmentResponse> {
        val tasks = getTasks(householdId)
        val allAssignments = mutableListOf<TaskAssignmentResponse>()
        for (task in tasks) {
            try {
                val assignments = getAssignments(householdId, task.id)
                allAssignments.addAll(assignments)
            } catch (_: Exception) {
                // Task has no assignments yet — skip
            }
        }
        return allAssignments
    }

    /**
     * Complete a task assignment. Calculates penalty if overdue, handles recurrence.
     * Returns the updated assignment.
     */
    suspend fun completeAssignment(
        householdId: String,
        taskId: String,
        task: TaskResponse,
        assignmentId: String,
        assignment: TaskAssignmentResponse
    ): TaskAssignmentResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val onTime = now <= assignment.dueDate

        // Calculate points (with penalty if overdue)
        val pointsAwarded = if (onTime) {
            task.points
        } else {
            val penalty = calculatePenalty(task, assignment.dueDate, now)
            maxOf(task.points - penalty, 0)
        }

        // Update assignment
        val fields = mapOf<String, FirestoreValue>(
            "status" to FirestoreValue(stringValue = "completed"),
            "completedAt" to FirestoreValue(integerValue = now.toString()),
            "pointsAwarded" to FirestoreValue(integerValue = pointsAwarded.toString()),
            "onTime" to FirestoreValue(booleanValue = onTime)
        )

        client.patch(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments/$assignmentId"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "status,completedAt,pointsAwarded,onTime")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        // Handle recurrence: create next assignment for recurring tasks
        if (task.frequency != "once" && task.recurrenceDays.isNotEmpty()) {
            val nextDueDate = calculateNextDueDate(task, now)
            if (nextDueDate != null) {
                // Create next assignment for the same members
                assignTask(
                    householdId = householdId,
                    taskId = taskId,
                    memberIds = listOf(assignment.memberId),
                    mandatory = assignment.mandatory,
                    dueDate = nextDueDate
                )
            }
        }

        return assignment.copy(
            status = "completed",
            completedAt = now,
            pointsAwarded = pointsAwarded,
            onTime = onTime
        )
    }

    // ────────────────────────────────────────────────────────
    //  Penalty & Recurrence Logic
    // ────────────────────────────────────────────────────────

    /**
     * Calculate penalty points for an overdue task.
     *
     * - fixed mode: subtracts `penaltyValue` per interval
     * - percentage mode: subtracts `penaltyValue`% of task.points per interval
     * - Capped at `penaltyMax` (which should not exceed task.points)
     */
    private fun calculatePenalty(task: TaskResponse, dueDate: Long, now: Long): Int {
        val mode = task.penaltyMode ?: return 0
        if (now <= dueDate) return 0

        val overdueMs = now - dueDate
        val intervalMs = when (task.penaltyInterval) {
            "week" -> 7L * 24 * 60 * 60 * 1000
            "month" -> 30L * 24 * 60 * 60 * 1000
            else -> 24L * 60 * 60 * 1000 // day
        }

        val intervals = (overdueMs / intervalMs).toInt() + 1 // +1 because first interval starts immediately

        val penalty = when (mode) {
            "fixed" -> task.penaltyValue * intervals
            "percentage" -> (task.points * task.penaltyValue * intervals) / 100
            else -> 0
        }

        // Cap at penaltyMax (if set) and never go below 0
        val capped = if (task.penaltyMax > 0) minOf(penalty, task.penaltyMax) else penalty
        return minOf(capped, task.points)
    }

    /**
     * Calculate the next due date for a recurring task.
     *
     * Given the current time, finds the next occurrence based on recurrenceDays.
     * If frequency is "daily", returns next day.
     * If frequency is "weekly", returns the next matching weekday from recurrenceDays.
     * If frequency is "monthly", returns the same day next month.
     */
    private fun calculateNextDueDate(task: TaskResponse, afterMs: Long): Long? {
        val tz = TimeZone.currentSystemDefault()
        val afterInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(afterMs)
        val afterDateTime = afterInstant.toLocalDateTime(tz)
        val afterDate = afterDateTime.date

        // Compute epoch millis for a given date at 12:00 local time
        // by going through Instant -> LocalDateTime -> back to Instant
        fun dateToEpoch(year: Int, month: Int, day: Int): Long {
            // Use the afterDateTime's offset by computing from Instant
            val baseInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(afterMs)
            val baseLocal = baseInstant.toLocalDateTime(tz)
            // Build target LocalDateTime
            val targetLdt = LocalDateTime(year, month, day, 12, 0, 0)
            // Compute the epoch using the timezone offset from base
            return targetLdt.toInstant(tz).toEpochMilliseconds()
        }

        val nextDate: LocalDate? = when (task.frequency) {
            "daily" -> afterDate.plus(1, DateTimeUnit.DAY)
            "weekly" -> {
                if (task.recurrenceDays.isEmpty()) {
                    afterDate.plus(7, DateTimeUnit.DAY)
                } else {
                    // Find the next matching day of the week
                    var candidate = afterDate.plus(1, DateTimeUnit.DAY)
                    var safety = 0
                    while (safety < 14) {
                        val dayOfWeek = candidate.dayOfWeek.ordinal + 1 // 1=Monday
                        if (dayOfWeek in task.recurrenceDays) {
                            return dateToEpoch(candidate.year, candidate.monthNumber, candidate.dayOfMonth)
                        }
                        candidate = candidate.plus(1, DateTimeUnit.DAY)
                        safety++
                    }
                    null // Should not happen
                }
            }
            "monthly" -> {
                // Next month, same day (capped at month length)
                val nextMonth = afterDate.monthNumber + 1
                val nextYear = if (nextMonth > 12) afterDate.year + 1 else afterDate.year
                val nextMonthNum = if (nextMonth > 12) nextMonth - 12 else nextMonth
                val maxDay = daysInMonth(nextYear, nextMonthNum)
                val dayOfMonth = minOf(afterDate.dayOfMonth, maxDay)
                LocalDate(nextYear, nextMonthNum, dayOfMonth)
            }
            else -> afterDate.plus(1, DateTimeUnit.DAY)
        }

        return nextDate?.let { dateToEpoch(it.year, it.monthNumber, it.dayOfMonth) }
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            else -> 30
        }
    }

    // ────────────────────────────────────────────────────────
    //  Task helpers
    // ────────────────────────────────────────────────────────

    suspend fun updateTask(
        householdId: String,
        taskId: String,
        title: String,
        description: String,
        points: Int,
        frequency: String,
        recurrenceDays: List<Int>,
        tags: List<String>,
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        assignmentRotation: List<org.taskhub.network.models.AssignmentSlot> = emptyList()
    ) {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "points" to FirestoreValue(integerValue = points.toString()),
            "frequency" to FirestoreValue(stringValue = frequency),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        // Tags as array
        fields["tags"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = tags.map { FirestoreValue(stringValue = it) }
            )
        )

        // Recurrence days as array
        fields["recurrenceDays"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = recurrenceDays.map { FirestoreValue(integerValue = it.toString()) }
            )
        )

        // Penalty configuration
        if (penaltyMode != null) {
            fields["penaltyMode"] = FirestoreValue(stringValue = penaltyMode)
            fields["penaltyValue"] = FirestoreValue(integerValue = penaltyValue.toString())
            fields["penaltyInterval"] = FirestoreValue(stringValue = penaltyInterval)
            fields["penaltyMax"] = FirestoreValue(integerValue = penaltyMax.toString())
        } else {
            fields["penaltyMode"] = FirestoreValue(nullValue = "NULL_VALUE")
            fields["penaltyValue"] = FirestoreValue(nullValue = "NULL_VALUE")
            fields["penaltyInterval"] = FirestoreValue(nullValue = "NULL_VALUE")
            fields["penaltyMax"] = FirestoreValue(nullValue = "NULL_VALUE")
        }

        // Assignment rotation as array of maps
        fields["assignmentRotation"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = assignmentRotation.map { slot ->
                    FirestoreValue(
                        mapValue = FirestoreMapValue(
                            fields = mapOf(
                                "dayOfWeek" to FirestoreValue(integerValue = slot.dayOfWeek.toString()),
                                "memberId" to FirestoreValue(stringValue = slot.memberId)
                            )
                        )
                    )
                }
            )
        )

        val updateMask = fields.keys.joinToString(",")

        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", updateMask)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Delete a task document.
     */
    suspend fun deleteTask(householdId: String, taskId: String) {
        client.delete("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
        }
    }

    private fun toTaskResponse(doc: FirestoreDocumentResponse, householdId: String): TaskResponse {
        val f = doc.fields
        return TaskResponse(
            id = extractDocId(doc.name),
            householdId = f["householdId"]?.stringValue ?: householdId,
            createdBy = f["createdBy"]?.stringValue ?: "",
            title = f["title"]?.stringValue ?: "",
            description = f["description"]?.stringValue ?: "",
            points = f["points"]?.integerValue?.toIntOrNull() ?: 10,
            frequency = f["frequency"]?.stringValue ?: "once",
            recurrenceDays = f["recurrenceDays"]?.arrayValue?.values
                ?.mapNotNull { it.integerValue?.toIntOrNull() } ?: emptyList(),
            tags = f["tags"]?.arrayValue?.values
                ?.mapNotNull { it.stringValue } ?: emptyList(),
            penaltyMode = f["penaltyMode"]?.stringValue,
            penaltyValue = f["penaltyValue"]?.integerValue?.toIntOrNull() ?: 0,
            penaltyInterval = f["penaltyInterval"]?.stringValue ?: "day",
            penaltyMax = f["penaltyMax"]?.integerValue?.toIntOrNull() ?: 0,
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            lastCompletedDate = f["lastCompletedDate"]?.integerValue?.toLongOrNull(),
            assignmentRotation = f["assignmentRotation"]?.arrayValue?.values
                ?.mapNotNull { slotValue ->
                    val sf = slotValue.mapValue?.fields ?: return@mapNotNull null
                    val dow = sf["dayOfWeek"]?.integerValue?.toIntOrNull() ?: return@mapNotNull null
                    val mid = sf["memberId"]?.stringValue ?: return@mapNotNull null
                    org.taskhub.network.models.AssignmentSlot(dayOfWeek = dow, memberId = mid)
                } ?: emptyList(),
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
            updatedAt = f["updatedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    private fun toTaskAssignmentResponse(
        doc: FirestoreDocumentResponse,
        taskId: String
    ): TaskAssignmentResponse {
        val f = doc.fields
        return TaskAssignmentResponse(
            id = extractDocId(doc.name),
            taskId = f["taskId"]?.stringValue ?: taskId,
            memberId = f["memberId"]?.stringValue ?: "",
            mandatory = f["mandatory"]?.booleanValue ?: false,
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            status = f["status"]?.stringValue ?: "assigned",
            completedAt = f["completedAt"]?.integerValue?.toLongOrNull(),
            pointsAwarded = f["pointsAwarded"]?.integerValue?.toIntOrNull(),
            onTime = f["onTime"]?.booleanValue,
            assignedAt = f["assignedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    // ────────────────────────────────────────────────────────
    //  Comments (subcollection under households/{id}/tasks/{taskId})
    // ────────────────────────────────────────────────────────

    /** Add a comment to a task. */
    suspend fun addComment(
        householdId: String,
        taskId: String,
        authorName: String,
        text: String
    ): org.taskhub.network.models.CommentResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "authorName" to FirestoreValue(stringValue = authorName),
            "text" to FirestoreValue(stringValue = text),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/tasks/$taskId/comments"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return org.taskhub.network.models.CommentResponse(id, authorName, text, now)
    }

    /** List comments for a task. */
    suspend fun getComments(
        householdId: String,
        taskId: String
    ): List<org.taskhub.network.models.CommentResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/tasks/$taskId/comments"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { doc ->
            val f = doc.fields
            org.taskhub.network.models.CommentResponse(
                id = extractDocId(doc.name),
                authorName = f["authorName"]?.stringValue ?: "",
                text = f["text"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }
    }

    // ────────────────────────────────────────────────────────
    //  Notifications (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Create a notification document for a member. */
    suspend fun createNotification(
        householdId: String,
        memberId: String,
        taskId: String,
        title: String,
        message: String
    ): NotificationResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "memberId" to FirestoreValue(stringValue = memberId),
            "taskId" to FirestoreValue(stringValue = taskId),
            "title" to FirestoreValue(stringValue = title),
            "message" to FirestoreValue(stringValue = message),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "read" to FirestoreValue(booleanValue = false)
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/notifications"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return NotificationResponse(id, memberId, taskId, title, message, now, read = false)
    }

    /** Get all notifications for a household. */
    suspend fun getNotifications(householdId: String): List<NotificationResponse> {
        return try {
            val response: FirestoreListResponse = client.get(
                "$baseUrl/households/$householdId/notifications"
            ) {
                tryAuthOrApiKey()
            }.body()

            response.documents.map { doc ->
                val f = doc.fields
                NotificationResponse(
                    id = extractDocId(doc.name),
                    memberId = f["memberId"]?.stringValue ?: "",
                    taskId = f["taskId"]?.stringValue ?: "",
                    title = f["title"]?.stringValue ?: "",
                    message = f["message"]?.stringValue ?: "",
                    createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
                    read = f["read"]?.booleanValue ?: false
                )
            }
        } catch (_: Exception) {
            emptyList() // No notifications subcollection yet
        }
    }

    /** Mark a notification as read. */
    suspend fun markNotificationRead(householdId: String, notificationId: String) {
        val fields = mapOf(
            "read" to FirestoreValue(booleanValue = true)
        )
        client.patch(
            "$baseUrl/households/$householdId/notifications/$notificationId"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "read")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    // ────────────────────────────────────────────────────────
    //  Request helpers
    // ────────────────────────────────────────────────────────

    /**
     * Tries Bearer auth first; falls back to API key parameter.
     * Used for read operations where API key alone might suffice.
     */
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() {
        try {
            ensureAuth()
            bearerToken?.let { header("Authorization", "Bearer $it") }
        } catch (_: Exception) {
            // Auth failed — fall back to API key for read-only access
            parameter("key", apiKey)
        }
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    /** Extract the document ID from the full Firestore resource name. */
    private fun extractDocId(resourceName: String): String =
        resourceName.substringAfterLast("/")

    private fun toHouseholdResponse(doc: FirestoreDocumentResponse): HouseholdResponse {
        val f = doc.fields
        return HouseholdResponse(
            id = extractDocId(doc.name),
            name = f["name"]?.stringValue ?: "",
            inviteCode = f["inviteCode"]?.stringValue ?: "",
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
            updatedAt = f["updatedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    private fun toMemberResponse(doc: FirestoreDocumentResponse, householdId: String): MemberResponse {
        val f = doc.fields
        return MemberResponse(
            id = extractDocId(doc.name),
            householdId = f["householdId"]?.stringValue ?: householdId,
            displayName = f["displayName"]?.stringValue ?: "",
            avatarUrl = f["avatarUrl"]?.stringValue,
            role = f["role"]?.stringValue ?: "child",
            totalPoints = f["totalPoints"]?.integerValue?.toIntOrNull() ?: 0,
            joinedAt = f["joinedAt"]?.integerValue?.toLongOrNull() ?: 0L,
            userId = f["userId"]?.stringValue,
            currentStreak = f["currentStreak"]?.integerValue?.toIntOrNull() ?: 0,
            bestStreak = f["bestStreak"]?.integerValue?.toIntOrNull() ?: 0,
            lastStreakDate = f["lastStreakDate"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        /**
         * Firebase Web API Key for task-hub-62f98.
         * Find it at: Firebase Console → Project Settings → General → Web API Key
         *
         * TODO: Replace with the real key from your Firebase project,
         *       or inject it via build config / environment.
         */
        const val DEFAULT_API_KEY = "\u00ABredacted:AIza\u2026\u00BB"
    }
}
