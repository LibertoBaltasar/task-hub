package org.taskhub.ui.models

import kotlinx.coroutines.CancellationException
import org.taskhub.network.FirestoreRepository
import org.taskhub.network.GoogleCalendarRepository
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.storage.SettingsStore

/**
 * Orquesta la sincronización automática de tareas con fecha → eventos en un
 * calendario de Google Calendar dedicado, uno por (usuario, espacio):
 * "Tareas personal" para el espacio Personal, "Tareas <hogar>" para hogares
 * compartidos.
 *
 * Solo sincroniza las asignaciones del usuario actual — cada persona ve sus
 * propias tareas en su propio calendario (ver [FirestoreRepository.resolveCurrentMember]).
 * Un `googleEventId` en la asignación (Firestore) marca que ya está sincronizada.
 *
 * Toda operación es best-effort: nunca lanza excepciones. Si no hay cuenta
 * vinculada o el token falla (consentimiento pendiente, offline, revocado),
 * se salta en silencio — [reconcile] se encarga de recuperar el estado más
 * tarde, sin bloquear la UI ni el flujo de tareas.
 */
class CalendarSyncManager(
    private val repo: FirestoreRepository,
    private val calendarRepo: GoogleCalendarRepository,
    private val settingsStore: SettingsStore,
    private val authManager: GoogleAuthManager
) {

    /** Nombre del calendario dedicado para este espacio. */
    private fun calendarName(householdName: String, isPersonal: Boolean): String =
        if (isPersonal) "Tareas personal" else "Tareas $householdName"

    /** Devuelve el calendarId cacheado localmente, o lo crea/busca y lo cachea. */
    private suspend fun ensureCalendarId(
        householdId: String,
        householdName: String,
        isPersonal: Boolean,
        accessToken: String
    ): String? {
        settingsStore.getCalendarId(householdId)?.let { return it }
        return try {
            val id = calendarRepo.ensureCalendar(accessToken, calendarName(householdName, isPersonal))
            settingsStore.setCalendarId(householdId, id)
            id
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Al asignar una tarea (crear o reasignar): crea un evento en Calendar para
     * cada asignación mía con fecha, y persiste el `googleEventId`. Las
     * asignaciones sin fecha (`dueDate == 0`) o de otros miembros se ignoran.
     */
    suspend fun onTaskAssigned(
        householdId: String,
        householdName: String,
        isPersonal: Boolean,
        assignments: List<TaskAssignmentResponse>
    ) {
        if (!settingsStore.isCalendarSyncEnabled()) return
        try {
            val myMemberId = repo.resolveCurrentMember(householdId)
            val mine = assignments.filter { it.memberId == myMemberId && it.dueDate > 0 }
            if (mine.isEmpty()) return

            val token = authManager.ensureCalendarAccessToken() ?: return
            val calendarId = ensureCalendarId(householdId, householdName, isPersonal, token) ?: return
            val tasks = try {
                repo.getTasks(householdId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }

            for (assignment in mine) {
                createEventForAssignment(householdId, calendarId, token, assignment, tasks)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: se reintenta en el próximo reconcile.
        }
    }

    /** Al desasignar/borrar una tarea: borra el evento vinculado (si lo hay) y limpia el campo. */
    suspend fun onTaskUnassigned(householdId: String, assignment: TaskAssignmentResponse) {
        if (!settingsStore.isCalendarSyncEnabled()) return
        deleteEventForAssignment(householdId, assignment)
    }

    /** Al completar una tarea: el evento ya no tiene sentido, se borra igual que al desasignar. */
    suspend fun onTaskCompleted(householdId: String, assignment: TaskAssignmentResponse) {
        if (!settingsStore.isCalendarSyncEnabled()) return
        deleteEventForAssignment(householdId, assignment)
    }

    /** Si cambia la fecha límite de una asignación ya sincronizada, actualiza el evento existente. */
    suspend fun onDueDateChanged(
        householdId: String,
        householdName: String,
        isPersonal: Boolean,
        assignment: TaskAssignmentResponse,
        newDueDate: Long,
        taskTitle: String,
        taskDescription: String = ""
    ) {
        if (!settingsStore.isCalendarSyncEnabled()) return
        try {
            val myMemberId = repo.resolveCurrentMember(householdId)
            if (assignment.memberId != myMemberId) return

            val token = authManager.ensureCalendarAccessToken() ?: return
            val eventId = assignment.googleEventId
            if (eventId == null) {
                if (newDueDate <= 0) return
                val calendarId = ensureCalendarId(householdId, householdName, isPersonal, token) ?: return
                val event = calendarRepo.createEvent(
                    accessToken = token,
                    calendarId = calendarId,
                    summary = taskTitle,
                    description = taskDescription,
                    dueDateEpochMs = newDueDate
                )
                repo.updateAssignmentGoogleEventId(householdId, assignment.taskId, assignment.id, event.id)
                return
            }

            val calendarId = settingsStore.getCalendarId(householdId) ?: return
            if (newDueDate <= 0) {
                calendarRepo.deleteEvent(token, calendarId, eventId)
                repo.updateAssignmentGoogleEventId(householdId, assignment.taskId, assignment.id, null)
            } else {
                calendarRepo.updateEvent(
                    accessToken = token,
                    calendarId = calendarId,
                    eventId = eventId,
                    summary = taskTitle,
                    description = taskDescription,
                    dueDateEpochMs = newDueDate
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: se reintenta en el próximo reconcile.
        }
    }

    /**
     * Al abrir un espacio (hogar/Personal): backfillea las asignaciones mías con
     * fecha que aún no tienen `googleEventId` (p. ej. porque el token no estaba
     * vinculado cuando se crearon). Idempotente — no toca nada que ya esté bien.
     */
    suspend fun reconcile(householdId: String, householdName: String, isPersonal: Boolean) {
        if (!settingsStore.isCalendarSyncEnabled()) return
        try {
            val myMemberId = repo.resolveCurrentMember(householdId)
            val assignments = repo.getAllAssignments(householdId)
            val pending = assignments.filter {
                it.memberId == myMemberId &&
                    it.dueDate > 0 &&
                    it.googleEventId == null &&
                    it.status != "completed"
            }
            if (pending.isEmpty()) return

            val token = authManager.ensureCalendarAccessToken() ?: return
            val calendarId = ensureCalendarId(householdId, householdName, isPersonal, token) ?: return
            val tasks = try {
                repo.getTasks(householdId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }

            for (assignment in pending) {
                createEventForAssignment(householdId, calendarId, token, assignment, tasks)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: se reintenta en el próximo reconcile.
        }
    }

    /**
     * Sincroniza una asignación concreta bajo demanda (botón "Sincronizar ahora"
     * en el detalle de tarea). A diferencia del resto de métodos, ignora el
     * interruptor de sincronización automática — es una acción explícita del
     * usuario. Devuelve true si el evento quedó creado y enlazado.
     */
    suspend fun syncNow(
        householdId: String,
        householdName: String,
        isPersonal: Boolean,
        assignment: TaskAssignmentResponse,
        task: org.taskhub.network.models.TaskResponse
    ): Boolean {
        if (assignment.dueDate <= 0) return false
        val token = authManager.ensureCalendarAccessToken() ?: return false
        val calendarId = ensureCalendarId(householdId, householdName, isPersonal, token) ?: return false
        return try {
            val event = calendarRepo.createEvent(
                accessToken = token,
                calendarId = calendarId,
                summary = task.title,
                description = task.description,
                dueDateEpochMs = assignment.dueDate
            )
            repo.updateAssignmentGoogleEventId(householdId, assignment.taskId, assignment.id, event.id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun createEventForAssignment(
        householdId: String,
        calendarId: String,
        token: String,
        assignment: TaskAssignmentResponse,
        tasks: List<org.taskhub.network.models.TaskResponse>
    ) {
        try {
            val task = tasks.find { it.id == assignment.taskId }
            val event = calendarRepo.createEvent(
                accessToken = token,
                calendarId = calendarId,
                summary = task?.title ?: "Tarea",
                description = task?.description.orEmpty(),
                dueDateEpochMs = assignment.dueDate
            )
            repo.updateAssignmentGoogleEventId(householdId, assignment.taskId, assignment.id, event.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: esta asignación se reintenta en el próximo reconcile.
        }
    }

    private suspend fun deleteEventForAssignment(householdId: String, assignment: TaskAssignmentResponse) {
        val eventId = assignment.googleEventId ?: return
        try {
            val calendarId = settingsStore.getCalendarId(householdId) ?: return
            val token = authManager.ensureCalendarAccessToken() ?: return
            try {
                calendarRepo.deleteEvent(token, calendarId, eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Puede que ya no exista (borrado a mano) — igualmente limpiamos el campo.
            }
            repo.updateAssignmentGoogleEventId(householdId, assignment.taskId, assignment.id, null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort: si falla, el evento huérfano queda en Calendar hasta el próximo intento.
        }
    }
}
