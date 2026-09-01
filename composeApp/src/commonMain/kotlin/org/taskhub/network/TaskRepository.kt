package org.taskhub.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import org.taskhub.network.models.AssignmentSlot
import org.taskhub.network.models.CommentResponse
import org.taskhub.network.models.Subtask
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse
import org.taskhub.storage.TaskCache

/**
 * Tareas de un hogar (subcolecciones `households/{id}/tasks`, `taskHistory`,
 * `tasks/{taskId}/assignments` y `tasks/{taskId}/comments`). Extraído de
 * [FirestoreRepository] (ver docs/refactor-arquitectura-2026-08-31.md, punto
 * 6, fase 2.3). Lógica movida tal cual, sin cambios de comportamiento.
 *
 * NO incluye `completeTask`/`completeAssignment`/`reassignTaskCompletion` ni
 * sus helpers privados tan acoplados (`resolveCompletionOutcome`,
 * `calculatePenalty`, `calculateNextDueDate`, `markAssignmentCompleted`,
 * `syncAssignmentOnTaskCompleted`, `updateTaskHistoryMember`,
 * `findTaskHistoryRecord`) — todos otorgan o transfieren puntos
 * (`addMemberPoints`), la capa de puntos que se moverá junto con
 * `MemberRepository` en la fase 2.5. Se quedan en [FirestoreRepository] hasta
 * esa fase para no crear una dependencia circular `TaskRepository` ↔
 * `MemberRepository` (que aún no existe) — ver el resumen del encargo.
 */
class TaskRepository(
    private val baseUrl: String,
    private val firestoreClient: FirestoreClient,
    private val taskCache: TaskCache,
    private val notificationRepository: NotificationRepository
) {
    private val client = firestoreClient.client

    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() = with(firestoreClient) { tryAuthOrApiKey() }
    private fun HttpRequestBuilder.updateMaskFieldPaths(vararg fields: String) =
        with(firestoreClient) { updateMaskFieldPaths(*fields) }
    private fun HttpRequestBuilder.updateMaskFieldPaths(fields: Collection<String>) =
        with(firestoreClient) { updateMaskFieldPaths(fields) }
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)

    // ────────────────────────────────────────────────────────
    //  Serialización compartida entre createTask/updateTask
    // ────────────────────────────────────────────────────────
    // Extraídos porque createTask y updateTask reimplementaban el mismo
    // mapeo (tags/recurrenceDays/subtasks/assignmentRotation/penalty) byte a
    // byte. Cada función sigue decidiendo POR SU CUENTA si incluye el campo
    // (createTask lo omite cuando está vacío/null; updateTask siempre lo
    // incluye, con NULL_VALUE explícito para borrar el valor previo vía
    // updateMask) — solo se comparte la construcción del valor en sí, sin
    // cambiar ese comportamiento observable.

    private fun tagsField(tags: List<String>): FirestoreValue =
        FirestoreValue(arrayValue = FirestoreArrayValue(values = tags.map { FirestoreValue(stringValue = it) }))

    private fun recurrenceDaysField(recurrenceDays: List<Int>): FirestoreValue =
        FirestoreValue(arrayValue = FirestoreArrayValue(values = recurrenceDays.map { FirestoreValue(integerValue = it.toString()) }))

    private fun subtasksField(subtasks: List<Subtask>): FirestoreValue = FirestoreValue(
        arrayValue = FirestoreArrayValue(
            values = subtasks.map { st ->
                FirestoreValue(
                    mapValue = FirestoreMapValue(
                        fields = mapOf(
                            "id" to FirestoreValue(stringValue = st.id),
                            "text" to FirestoreValue(stringValue = st.text),
                            "completed" to FirestoreValue(booleanValue = st.completed)
                        )
                    )
                )
            }
        )
    )

    private fun assignmentRotationField(assignmentRotation: List<AssignmentSlot>): FirestoreValue = FirestoreValue(
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

    /** Campos de penalización cuando [mode] no es null; mapa vacío si lo es (el caller decide si añadirlos u omitirlos). */
    private fun penaltyFields(mode: String?, value: Int, interval: String, max: Int): Map<String, FirestoreValue> =
        if (mode == null) emptyMap() else mapOf(
            "penaltyMode" to FirestoreValue(stringValue = mode),
            "penaltyValue" to FirestoreValue(integerValue = value.toString()),
            "penaltyInterval" to FirestoreValue(stringValue = interval),
            "penaltyMax" to FirestoreValue(integerValue = max.toString())
        )

    /** Igual que [penaltyFields], pero para updates: si [mode] es null, limpia los 4 campos con NULL_VALUE en vez de omitirlos. */
    private fun penaltyFieldsOrClear(mode: String?, value: Int, interval: String, max: Int): Map<String, FirestoreValue> =
        if (mode != null) penaltyFields(mode, value, interval, max) else mapOf(
            "penaltyMode" to FirestoreValue(nullValue = "NULL_VALUE"),
            "penaltyValue" to FirestoreValue(nullValue = "NULL_VALUE"),
            "penaltyInterval" to FirestoreValue(nullValue = "NULL_VALUE"),
            "penaltyMax" to FirestoreValue(nullValue = "NULL_VALUE")
        )

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
        recurrenceDay: Int? = null,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        dueDate: Long = 0,
        assignmentRotation: List<AssignmentSlot> = emptyList()
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
        fields["tags"] = tagsField(tags)

        // Recurrence days as array
        if (recurrenceDays.isNotEmpty()) {
            fields["recurrenceDays"] = recurrenceDaysField(recurrenceDays)
        }

        // Recurrence day of month (solo "monthly")
        if (recurrenceDay != null) {
            fields["recurrenceDay"] = FirestoreValue(integerValue = recurrenceDay.toString())
        }

        // Penalty configuration
        fields.putAll(penaltyFields(penaltyMode, penaltyValue, penaltyInterval, penaltyMax))

        // Assignment rotation as array of maps
        if (assignmentRotation.isNotEmpty()) {
            fields["assignmentRotation"] = assignmentRotationField(assignmentRotation)
        }

        // Subtasks as array of maps
        fields["subtasks"] = subtasksField(subtasks)

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households/$householdId/tasks") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createTask")
        taskCache.clearTasks(householdId)
        return TaskResponse(
            id = id, householdId = householdId, createdBy = createdBy,
            title = title, description = description, points = points,
            frequency = frequency, recurrenceDays = recurrenceDays,
            recurrenceDay = recurrenceDay, tags = tags,
            subtasks = subtasks,
            penaltyMode = penaltyMode, penaltyValue = penaltyValue,
            penaltyInterval = penaltyInterval, penaltyMax = penaltyMax,
            dueDate = dueDate, lastCompletedDate = null,
            assignmentRotation = assignmentRotation,
            createdAt = now, updatedAt = now
        )
    }

    /**
     * List all tasks for a household. Falls back to local cache if offline.
     *
     * Un 404/403 es una señal DEFINITIVA (hogar borrado / acceso perdido, igual
     * que en `getHousehold`) y se relanza en vez de devolver la caché stale;
     * solo fallos de transporte/servidor caen a caché.
     */
    suspend fun getTasks(householdId: String): List<TaskResponse> {
        return try {
            val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/tasks") {
                tryAuthOrApiKey()
            }.body()

            val tasks = response.documents.map { toTaskResponse(it, householdId) }
            taskCache.cacheTasks(householdId, tasks)
            tasks
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirestoreException) {
            if (e.statusCode == 404 || e.statusCode == 403) throw e
            taskCache.getCachedTasks(householdId) ?: throw e
        } catch (e: Exception) {
            taskCache.getCachedTasks(householdId) ?: throw e
        }
    }

    /** Get a single task by id. Used where only one task is needed (avoids an N+1 full-list fetch). */
    suspend fun getTask(householdId: String, taskId: String): TaskResponse {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$householdId/tasks/$taskId") {
            tryAuthOrApiKey()
        }.body()
        return toTaskResponse(response, householdId)
    }

    /**
     * Revert a task completion — used by the undo feature.
     * Restores the previous lastCompletedDate/completedBy on the task document.
     * No revierte puntos/racha/historial por sí sola — eso lo hace el caller
     * (ver `TaskScreenModel.undoCompleteTask`: `addMemberPoints`, `updateMemberStreak`
     * y `deleteTaskHistoryRecord`).
     */
    suspend fun revertTaskCompletion(
        householdId: String,
        taskId: String,
        previousLastCompletedDate: Long?,
        previousCompletedBy: String? = null
    ) {
        val lcdValue = if (previousLastCompletedDate != null) {
            FirestoreValue(integerValue = previousLastCompletedDate.toString())
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }
        val cbValue = if (previousCompletedBy != null) {
            FirestoreValue(stringValue = previousCompletedBy)
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }
        val fields = mapOf(
            "lastCompletedDate" to lcdValue,
            "completedBy" to cbValue
        )
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            updateMaskFieldPaths("lastCompletedDate", "completedBy")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
        taskCache.clearTasks(householdId)
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
    suspend fun getTaskHistory(householdId: String): List<TaskHistoryResponse> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/taskHistory"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            TaskHistoryResponse(
                id = extractDocId(doc.name, "getTaskHistory"),
                taskId = f["taskId"]?.stringValue ?: "",
                memberId = f["memberId"]?.stringValue ?: "",
                points = f["points"]?.integerValue?.toIntOrNull() ?: 0,
                completedAt = f["completedAt"]?.integerValue?.toLongOrNull() ?: 0L,
                onTime = f["onTime"]?.booleanValue ?: true
            )
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

            val id = extractDocId(response.name, "assignTask")
            results.add(TaskAssignmentResponse(
                id = id, taskId = taskId, memberId = memberId,
                mandatory = mandatory, dueDate = dueDate, status = "assigned",
                assignedAt = now
            ))

            // Create notification for the assigned member
            notificationRepository.createNotification(
                householdId = householdId,
                memberId = memberId,
                taskId = taskId,
                title = "📋 Tarea asignada",
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

    /** Borra todas las asignaciones de una tarea (para reasignar al editar). */
    suspend fun deleteAssignments(householdId: String, taskId: String) {
        val assignments = try {
            getAssignments(householdId, taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        deleteAssignmentDocs(householdId, taskId, assignments)
    }

    private suspend fun deleteAssignmentDocs(
        householdId: String,
        taskId: String,
        assignments: List<TaskAssignmentResponse>
    ) {
        assignments.forEach { assignment ->
            try {
                client.delete("$baseUrl/households/$householdId/tasks/$taskId/assignments/${assignment.id}") {
                    withAuth()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No crítico: si ya no existe, seguimos.
            }
        }
    }

    /**
     * Sustituye las asignaciones de una tarea por unas nuevas (usado al editar
     * la tarea desde `EditTaskScreen`).
     *
     * Mitigación de atomicidad (ver `docs/atomicidad-commit-pendiente.md`,
     * sección `updateTask`): antes esta operación era `deleteAssignments` +
     * `assignTask` como dos pasos independientes en el caller — si la creación
     * de las nuevas asignaciones fallaba a mitad de camino (p. ej. tras crear
     * la asignación de 2 de 3 miembros), la tarea ya se había quedado sin
     * ninguna asignación previa, así que el resultado era "tarea con solo 2
     * asignaciones" en el mejor caso o "sin ninguna" si fallaba en el primer
     * miembro. Aquí se invierte el orden: se crean las asignaciones nuevas
     * PRIMERO y solo se borran las antiguas si esa creación no lanzó. Si el
     * paso de creación falla, la tarea conserva sus asignaciones previas
     * (estado recuperable) en vez de quedarse sin ninguna. Sigue sin ser
     * atómico de extremo a extremo (un fallo justo en el borrado de las
     * antiguas puede dejar antiguas + nuevas duplicadas, un estado peor que
     * "sin cambios" pero mejor que "sin asignaciones").
     */
    suspend fun replaceAssignments(
        householdId: String,
        taskId: String,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long,
        taskTitle: String = ""
    ): List<TaskAssignmentResponse> {
        val previous = try {
            getAssignments(householdId, taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val created = if (memberIds.isNotEmpty()) {
            assignTask(householdId, taskId, memberIds, mandatory, dueDate, taskTitle)
        } else {
            emptyList()
        }

        deleteAssignmentDocs(householdId, taskId, previous)

        return created
    }

    /** Get all assignments across all tasks for a household (peticiones en paralelo). */
    suspend fun getAllAssignments(householdId: String): List<TaskAssignmentResponse> {
        val tasks = getTasks(householdId)
        return coroutineScope {
            tasks.map { task ->
                async {
                    try {
                        getAssignments(householdId, task.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList() // Task has no assignments yet — skip
                    }
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Vincula/desvincula el evento de Google Calendar de una asignación.
     * `googleEventId = null` limpia el campo (p. ej. tras borrar el evento).
     */
    suspend fun updateAssignmentGoogleEventId(
        householdId: String,
        taskId: String,
        assignmentId: String,
        googleEventId: String?
    ) {
        val fields = mapOf(
            "googleEventId" to if (googleEventId != null) {
                FirestoreValue(stringValue = googleEventId)
            } else {
                FirestoreValue(nullValue = "NULL_VALUE")
            }
        )
        client.patch(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments/$assignmentId"
        ) {
            withAuth()
            updateMaskFieldPaths("googleEventId")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
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
        recurrenceDay: Int? = null,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        assignmentRotation: List<AssignmentSlot> = emptyList(),
        dueDate: Long = 0
    ) {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "points" to FirestoreValue(integerValue = points.toString()),
            "frequency" to FirestoreValue(stringValue = frequency),
            "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        // Tags as array
        fields["tags"] = tagsField(tags)

        // Recurrence days as array
        fields["recurrenceDays"] = recurrenceDaysField(recurrenceDays)

        // Recurrence day of month (solo "monthly"); null borra el valor previo.
        fields["recurrenceDay"] = if (recurrenceDay != null) {
            FirestoreValue(integerValue = recurrenceDay.toString())
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }

        // Penalty configuration
        fields.putAll(penaltyFieldsOrClear(penaltyMode, penaltyValue, penaltyInterval, penaltyMax))

        // Assignment rotation as array of maps
        fields["assignmentRotation"] = assignmentRotationField(assignmentRotation)

        // Subtasks as array of maps
        fields["subtasks"] = subtasksField(subtasks)

        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            updateMaskFieldPaths(fields.keys)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
        taskCache.clearTasks(householdId)
    }

    /**
     * Update only the subtasks array on a task document.
     * Used for quick toggling of individual subtask checkboxes.
     */
    suspend fun updateSubtasks(
        householdId: String,
        taskId: String,
        subtasks: List<Subtask>
    ) {
        val fields = mapOf(
            "subtasks" to FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = subtasks.map { st ->
                        FirestoreValue(
                            mapValue = FirestoreMapValue(
                                fields = mapOf(
                                    "id" to FirestoreValue(stringValue = st.id),
                                    "text" to FirestoreValue(stringValue = st.text),
                                    "completed" to FirestoreValue(booleanValue = st.completed)
                                )
                            )
                        )
                    }
                )
            )
        )
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            updateMaskFieldPaths("subtasks")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
        taskCache.clearTasks(householdId)
    }

    /**
     * Delete a task document.
     */
    suspend fun deleteTask(householdId: String, taskId: String) {
        client.delete("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
        }
        taskCache.clearTasks(householdId)
    }

    private fun toTaskResponse(doc: FirestoreDocumentResponse, householdId: String): TaskResponse {
        val f = doc.fields
        return TaskResponse(
            id = extractDocId(doc.name, "getTasks"),
            householdId = f["householdId"]?.stringValue ?: householdId,
            createdBy = f["createdBy"]?.stringValue ?: "",
            title = f["title"]?.stringValue ?: "",
            description = f["description"]?.stringValue ?: "",
            points = f["points"]?.integerValue?.toIntOrNull() ?: 10,
            frequency = f["frequency"]?.stringValue ?: "once",
            recurrenceDays = f["recurrenceDays"]?.arrayValue?.values
                ?.mapNotNull { it.integerValue?.toIntOrNull() } ?: emptyList(),
            recurrenceDay = f["recurrenceDay"]?.integerValue?.toIntOrNull(),
            tags = f["tags"]?.arrayValue?.values
                ?.mapNotNull { it.stringValue } ?: emptyList(),
            subtasks = f["subtasks"]?.arrayValue?.values
                ?.mapNotNull { stValue ->
                    val sf = stValue.mapValue?.fields ?: return@mapNotNull null
                    val sid = sf["id"]?.stringValue ?: return@mapNotNull null
                    val stext = sf["text"]?.stringValue ?: return@mapNotNull null
                    val scompleted = sf["completed"]?.booleanValue ?: false
                    Subtask(id = sid, text = stext, completed = scompleted)
                } ?: emptyList(),
            penaltyMode = f["penaltyMode"]?.stringValue,
            penaltyValue = f["penaltyValue"]?.integerValue?.toIntOrNull() ?: 0,
            penaltyInterval = f["penaltyInterval"]?.stringValue ?: "day",
            penaltyMax = f["penaltyMax"]?.integerValue?.toIntOrNull() ?: 0,
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            lastCompletedDate = f["lastCompletedDate"]?.integerValue?.toLongOrNull(),
            completedBy = f["completedBy"]?.stringValue,
            assignmentRotation = f["assignmentRotation"]?.arrayValue?.values
                ?.mapNotNull { slotValue ->
                    val sf = slotValue.mapValue?.fields ?: return@mapNotNull null
                    val dow = sf["dayOfWeek"]?.integerValue?.toIntOrNull() ?: return@mapNotNull null
                    val mid = sf["memberId"]?.stringValue ?: return@mapNotNull null
                    AssignmentSlot(dayOfWeek = dow, memberId = mid)
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
            id = extractDocId(doc.name, "getAssignments"),
            taskId = f["taskId"]?.stringValue ?: taskId,
            memberId = f["memberId"]?.stringValue ?: "",
            mandatory = f["mandatory"]?.booleanValue ?: false,
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            status = f["status"]?.stringValue ?: "assigned",
            completedAt = f["completedAt"]?.integerValue?.toLongOrNull(),
            pointsAwarded = f["pointsAwarded"]?.integerValue?.toIntOrNull(),
            onTime = f["onTime"]?.booleanValue,
            assignedAt = f["assignedAt"]?.integerValue?.toLongOrNull() ?: 0L,
            googleEventId = f["googleEventId"]?.stringValue
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
    ): CommentResponse {
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

        val id = extractDocId(response.name, "addComment")
        return CommentResponse(id, authorName, text, now)
    }

    /** List comments for a task. */
    suspend fun getComments(
        householdId: String,
        taskId: String
    ): List<CommentResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/tasks/$taskId/comments"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { doc ->
            val f = doc.fields
            CommentResponse(
                id = extractDocId(doc.name, "getComments"),
                authorName = f["authorName"]?.stringValue ?: "",
                text = f["text"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }
    }
}
