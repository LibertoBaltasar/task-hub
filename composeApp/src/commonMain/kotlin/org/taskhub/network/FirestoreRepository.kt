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
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskInstanceResponse
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
            "joinedAt" to FirestoreValue(integerValue = now.toString())
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
        return MemberResponse(id, householdId, displayName, avatarUrl, role, 0, now)
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
        penaltyMax: Int
    ): TaskResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "householdId" to FirestoreValue(stringValue = householdId),
            "createdBy" to FirestoreValue(stringValue = createdBy),
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "points" to FirestoreValue(integerValue = points.toString()),
            "frequency" to FirestoreValue(stringValue = frequency),
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

    /** Assign a task to one or more members with a due date. */
    suspend fun assignTask(
        householdId: String,
        taskId: String,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long
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
    //  Task Instances (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /**
     * Generate task instances for a recurring task.
     * - daily: 7 instances starting today
     * - weekly + recurrenceDays: instances on matching days for next 28 days
     * - monthly: 3 instances (current month + 2 following)
     * Returns the list of created instances.
     */
    suspend fun generateTaskInstances(
        householdId: String,
        taskId: String,
        frequency: String,
        recurrenceDays: List<Int>,
        points: Int
    ): List<TaskInstanceResponse> {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        val results = mutableListOf<TaskInstanceResponse>()

        when (frequency) {
            "daily" -> {
                for (i in 0 until 7) {
                    val date = today.plus(i, DateTimeUnit.DAY)
                    val dueDate = dateToEpoch(date.year, date.monthNumber, date.dayOfMonth, tz)
                    results.add(createInstance(householdId, taskId, dueDate, points))
                }
            }
            "weekly" -> {
                if (recurrenceDays.isNotEmpty()) {
                    // Generate for next 28 days
                    for (i in 0 until 28) {
                        val date = today.plus(i, DateTimeUnit.DAY)
                        val dayOfWeek = date.dayOfWeek.ordinal + 1 // 1=Monday
                        if (dayOfWeek in recurrenceDays) {
                            val dueDate = dateToEpoch(date.year, date.monthNumber, date.dayOfMonth, tz)
                            results.add(createInstance(householdId, taskId, dueDate, points))
                        }
                    }
                } else {
                    // Weekly with no specific days — generate 4 instances, one per week
                    for (i in 0 until 4) {
                        val date = today.plus(i * 7, DateTimeUnit.DAY)
                        val dueDate = dateToEpoch(date.year, date.monthNumber, date.dayOfMonth, tz)
                        results.add(createInstance(householdId, taskId, dueDate, points))
                    }
                }
            }
            "monthly" -> {
                // Current month + 2 more
                for (m in 0 until 3) {
                    val targetMonth = today.monthNumber + m
                    val year = today.year + (targetMonth - 1) / 12
                    val month = ((targetMonth - 1) % 12) + 1
                    val maxDay = daysInMonth(year, month)
                    val dayOfMonth = minOf(today.dayOfMonth, maxDay)
                    val date = LocalDate(year, month, dayOfMonth)
                    val dueDate = dateToEpoch(date.year, date.monthNumber, date.dayOfMonth, tz)
                    results.add(createInstance(householdId, taskId, dueDate, points))
                }
            }
            else -> {
                // "once" — single instance today
                val dueDate = dateToEpoch(today.year, today.monthNumber, today.dayOfMonth, tz)
                results.add(createInstance(householdId, taskId, dueDate, points))
            }
        }

        return results
    }

    /** Create a single task instance document. */
    private suspend fun createInstance(
        householdId: String,
        taskId: String,
        dueDate: Long,
        points: Int
    ): TaskInstanceResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf<String, FirestoreValue>(
            "taskId" to FirestoreValue(stringValue = taskId),
            "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
            "completed" to FirestoreValue(booleanValue = false),
            "skipped" to FirestoreValue(booleanValue = false),
            "pointsAwarded" to FirestoreValue(nullValue = "NULL_VALUE"),
            "completedAt" to FirestoreValue(nullValue = "NULL_VALUE"),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/taskInstances"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name)
        return TaskInstanceResponse(
            id = id, taskId = taskId, dueDate = dueDate,
            completed = false, createdAt = now
        )
    }

    /** List all task instances for a household. */
    suspend fun getTaskInstances(householdId: String): List<TaskInstanceResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/taskInstances"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { toTaskInstanceResponse(it) }
    }

    /**
     * Complete a task instance. Awards points and generates the next instance
     * for recurring tasks. Returns (completedInstance, nextInstance?) pair.
     */
    suspend fun completeTaskInstance(
        householdId: String,
        task: TaskResponse,
        instance: TaskInstanceResponse
    ): Pair<TaskInstanceResponse, TaskInstanceResponse?> {
        val now = Clock.System.now().toEpochMilliseconds()
        val pointsAwarded = task.points

        // Mark instance as completed
        val fields = mapOf(
            "completed" to FirestoreValue(booleanValue = true),
            "completedAt" to FirestoreValue(integerValue = now.toString()),
            "pointsAwarded" to FirestoreValue(integerValue = pointsAwarded.toString())
        )

        client.patch(
            "$baseUrl/households/$householdId/taskInstances/${instance.id}"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "completed,completedAt,pointsAwarded")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        val completed = instance.copy(
            completed = true,
            completedAt = now,
            pointsAwarded = pointsAwarded
        )

        // Generate next instance for recurring tasks
        val nextInstance = if (task.frequency != "once") {
            val nextDueDate = calculateNextInstanceDueDate(task, instance.dueDate)
            if (nextDueDate != null) {
                createInstance(householdId, task.id, nextDueDate, task.points)
            } else null
        } else null

        return Pair(completed, nextInstance)
    }

    /**
     * Update a task template. Only updates the task document — does NOT regenerate
     * instances or affect existing assignments. Uses Firestore PATCH with updateMask.
     */
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
        penaltyMax: Int
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

        val updateMask = fields.keys.joinToString(",")

        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", updateMask)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Delete a task and all its associated taskInstances.
     * Does NOT delete assignments (orphaned subcollections are harmless).
     */
    suspend fun deleteTask(householdId: String, taskId: String) {
        // 1. Delete all taskInstances for this task
        val allInstances = getTaskInstances(householdId)
        val taskInstances = allInstances.filter { it.taskId == taskId }
        for (instance in taskInstances) {
            client.delete(
                "$baseUrl/households/$householdId/taskInstances/${instance.id}"
            ) {
                withAuth()
            }
        }

        // 2. Delete the task document
        client.delete("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
        }
    }

    /**
     * Skip a task instance. Marks it as skipped and generates the next instance
     * for recurring tasks. Skipped instances do NOT award or deduct points.
     */
    suspend fun skipTaskInstance(
        householdId: String,
        task: TaskResponse,
        instance: TaskInstanceResponse
    ): Pair<TaskInstanceResponse, TaskInstanceResponse?> {
        val now = Clock.System.now().toEpochMilliseconds()

        // Mark instance as skipped
        val fields = mapOf(
            "skipped" to FirestoreValue(booleanValue = true)
        )

        client.patch(
            "$baseUrl/households/$householdId/taskInstances/${instance.id}"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "skipped")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        val skippedInstance = instance.copy(skipped = true)

        // Generate next instance for recurring tasks
        val nextInstance = if (task.frequency != "once") {
            val nextDueDate = calculateNextInstanceDueDate(task, instance.dueDate)
            if (nextDueDate != null) {
                createInstance(householdId, task.id, nextDueDate, task.points)
            } else null
        } else null

        return Pair(skippedInstance, nextInstance)
    }

    /**
     * Calculate the next instance due date based on the task's frequency and
     * the current instance's due date. Returns the epoch millis for the next date.
     */
    private fun calculateNextInstanceDueDate(task: TaskResponse, currentDueDate: Long): Long? {
        val tz = TimeZone.currentSystemDefault()
        val currentInstant = Instant.fromEpochMilliseconds(currentDueDate)
        val currentDate = currentInstant.toLocalDateTime(tz).date

        val nextDate = when (task.frequency) {
            "daily" -> currentDate.plus(1, DateTimeUnit.DAY)
            "weekly" -> {
                if (task.recurrenceDays.isNotEmpty()) {
                    // Find the next matching day of the week from recurrenceDays
                    var candidate = currentDate.plus(1, DateTimeUnit.DAY)
                    var safety = 0
                    while (safety < 14) {
                        val dayOfWeek = candidate.dayOfWeek.ordinal + 1 // 1=Monday
                        if (dayOfWeek in task.recurrenceDays) {
                            return dateToEpoch(candidate.year, candidate.monthNumber, candidate.dayOfMonth, tz)
                        }
                        candidate = candidate.plus(1, DateTimeUnit.DAY)
                        safety++
                    }
                    null
                } else {
                    currentDate.plus(7, DateTimeUnit.DAY)
                }
            }
            "monthly" -> {
                val nextMonth = currentDate.monthNumber + 1
                val nextYear = if (nextMonth > 12) currentDate.year + 1 else currentDate.year
                val nextMonthNum = if (nextMonth > 12) nextMonth - 12 else nextMonth
                val maxDay = daysInMonth(nextYear, nextMonthNum)
                val dayOfMonth = minOf(currentDate.dayOfMonth, maxDay)
                LocalDate(nextYear, nextMonthNum, dayOfMonth)
            }
            else -> null
        }

        return nextDate?.let { dateToEpoch(it.year, it.monthNumber, it.dayOfMonth, tz) }
    }

    /** Helper: convert a date to epoch millis (noon local time). */
    private fun dateToEpoch(year: Int, month: Int, day: Int, tz: TimeZone): Long {
        val ldt = LocalDateTime(year, month, day, 12, 0, 0)
        return ldt.toInstant(tz).toEpochMilliseconds()
    }

    // ────────────────────────────────────────────────────────
    //  Task helpers
    // ────────────────────────────────────────────────────────

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

    private fun toTaskInstanceResponse(doc: FirestoreDocumentResponse): TaskInstanceResponse {
        val f = doc.fields
        return TaskInstanceResponse(
            id = extractDocId(doc.name),
            taskId = f["taskId"]?.stringValue ?: "",
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            completed = f["completed"]?.booleanValue ?: false,
            completedAt = f["completedAt"]?.integerValue?.toLongOrNull(),
            pointsAwarded = f["pointsAwarded"]?.integerValue?.toIntOrNull(),
            skipped = f["skipped"]?.booleanValue ?: false,
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
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
            userId = f["userId"]?.stringValue
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
        const val DEFAULT_API_KEY = "AIzaSyCOSray4XhnZGdgT91U14KlByk6ySuyhW0"
    }
}
