package org.taskhub.network.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ── Request DTOs ──────────────────────────────────────────

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class JoinHouseholdRequest(val inviteCode: String)

@Serializable
data class CreateMemberRequest(
    val displayName: String,
    val role: String = "child",
    val avatarUrl: String? = null
)

// ── Response DTOs ─────────────────────────────────────────

@Immutable
@Serializable
data class HouseholdResponse(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** True si es el espacio "Personal" auto-creado (sin invitaciones). */
    val isPersonal: Boolean = false
)

/** Miembro de un hogar. Cada usuario que se une crea un Member doc. */
@Immutable
@Serializable
data class MemberResponse(
    val id: String,
    val householdId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /** "admin" | "child" */
    val role: String,
    /** Puntos acumulados totales (histórico). */
    val totalPoints: Int = 0,
    val joinedAt: Long = 0,
    /** ID del usuario anónimo de Firebase Auth (localId). */
    val userId: String? = null,
    /** Racha actual de días consecutivos completando tareas. */
    val currentStreak: Int = 0,
    /** Mejor racha histórica. */
    val bestStreak: Int = 0,
    /** Último día de racha registrado (epoch millis). 0 = sin racha. */
    val lastStreakDate: Long = 0,
    /** Epoch millis en que el miembro abandonó el hogar (soft-delete). 0 = activo. */
    val leftAt: Long = 0,
    /** Puntos ya DADOS agradeciendo a otros durante la semana de [appreciationWeekStart]. */
    val appreciationGiven: Int = 0,
    /** Epoch millis del lunes 00:00 local de la semana a la que corresponde [appreciationGiven]. */
    val appreciationWeekStart: Long = 0
)

/**
 * Perfil GLOBAL de un usuario, independiente de su membresía en hogares.
 * Vive en la colección `users/{userId}` y es la base del "perfilado creciente":
 * añadir foto, bio, preferencias, etc. solo requiere añadir campos aquí y en
 * [org.taskhub.network.FirestoreRepository.upsertUserProfile].
 */
@Serializable
data class UserProfile(
    /** UID de Firebase Auth (anónimo o Google). */
    val id: String,
    val displayName: String = "",
    /** URL de la foto de perfil. null = sin foto todavía. */
    val avatarUrl: String? = null,
    /** Emoji para el avatar rápido (alternativa sin foto). p.ej. "🧑", "👩", "🐱". */
    val avatarEmoji: String = "",
    /** Bio corta estilo "Papá, profe de mates y cocinero". */
    val bio: String = "",
    /** Estado tipo "🍳 Preparando la cena" o "📚 Estudiando". */
    val status: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class ErrorResponse(val error: String)

// ── Task DTOs ────────────────────────────────────────────

@Serializable
data class AssignmentSlot(
    /** 1=Lunes..7=Domingo */
    val dayOfWeek: Int,
    val memberId: String
)

@Serializable
data class Subtask(
    val id: String,
    val text: String,
    val completed: Boolean = false
)

/**
 * Representa una tarea en Firestore y en la UI.
 *
 * Modelo simplificado sin instancias:
 * - Una tarea "daily" es UN solo documento, no N documentos por día.
 * - La recurrencia se calcula en cliente vía [frequency] + [recurrenceDays].
 * - [lastCompletedDate] marca la última vez completada; sirve para saber si
 *   toca hoy (isTaskDueToday en TaskListScreen.kt).
 * - [dueDate] es la fecha límite para tareas "once" (0 = sin fecha).
 */
@Immutable
@Serializable
data class TaskResponse(
    val id: String,
    val householdId: String,
    val createdBy: String,
    val title: String,
    val description: String = "",
    val points: Int = 10,
    /** "once" | "daily" | "weekly" | "monthly" */
    val frequency: String = "once",
    /** Días de la semana en que aplica (1=Lunes..7=Domingo). Solo para "weekly". */
    val recurrenceDays: List<Int> = emptyList(),
    /**
     * Día del mes en que aplica (1..31). Solo para "monthly".
     * null = comportamiento legado: toca una vez al mes, cualquier día.
     * Si el mes no tiene ese día (p.ej. 31 en febrero), se ajusta al último
     * día del mes — ver [org.taskhub.network.RecurrenceRules.clampDayOfMonth].
     */
    val recurrenceDay: Int? = null,
    val tags: List<String> = emptyList(),
    /** Checklist de subtareas dentro de la tarea. */
    val subtasks: List<Subtask> = emptyList(),
    /** Modo de penalización por retraso: "fixed" (puntos fijos) o "percentage" (% de points). null = sin penalización. */
    val penaltyMode: String? = null,
    val penaltyValue: Int = 0,
    /** Unidad de intervalo de penalización: "day", "week", "month". */
    val penaltyInterval: String = "day",
    /** Tope máximo de penalización (0 = sin tope). */
    val penaltyMax: Int = 0,
    /**
     * Fecha límite en epoch millis (solo para tareas "once").
     * 0 = sin fecha límite.
     */
    val dueDate: Long = 0,
    /**
     * Última vez que se completó esta tarea (epoch millis).
     * null = nunca completada.
     * Es el campo central para el cálculo de "¿toca hoy?".
     */
    val lastCompletedDate: Long? = null,
    /**
     * ID del miembro que marcó la tarea como hecha la última vez.
     * null = nunca completada. Es la base de "quien marca hecho recibe los
     * puntos" (al margen de quién esté asignado) y de "editar quién la hizo".
     */
    val completedBy: String? = null,
    /** Rotación diaria de asignados: quién le toca cada día de la semana. */
    val assignmentRotation: List<AssignmentSlot> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * Asignación de una tarea a un miembro.
 * Una tarea puede tener 0..N asignaciones (una por miembro).
 */
@Immutable
@Serializable
data class TaskAssignmentResponse(
    val id: String,
    val taskId: String,
    val memberId: String,
    /** Si es obligatoria, el miembro no puede rechazarla. */
    val mandatory: Boolean = false,
    /** Fecha límite (epoch millis). */
    val dueDate: Long = 0,
    /** "assigned" | "completed" */
    val status: String = "assigned",
    /** Timestamp de compleción (epoch millis). null = no completada. */
    val completedAt: Long? = null,
    /** Puntos otorgados (puede ser menor por penalización). */
    val pointsAwarded: Int? = null,
    /** true = a tiempo, false = tarde, null = no completada aún. */
    val onTime: Boolean? = null,
    val assignedAt: Long = 0,
    /** ID del evento en Google Calendar vinculado, o null si no está sincronizada. */
    val googleEventId: String? = null
)

// ── Comments DTO ─────────────────────────────────────────

@Serializable
data class CommentResponse(
    val id: String,
    val authorName: String,
    val text: String,
    val createdAt: Long = 0
)

// ── Message DTO ────────────────────────────────────────────

@Serializable
data class MessageResponse(
    val id: String,
    val memberId: String,
    val authorName: String,
    val text: String,
    val createdAt: Long = 0
)

// ── Task History DTO ──────────────────────────────────────

@Serializable
data class TaskHistoryResponse(
    val id: String,
    val taskId: String,
    val memberId: String,
    val points: Int = 0,
    val completedAt: Long = 0,
    val onTime: Boolean = true
)

// ── Notification DTO ──────────────────────────────────────

@Serializable
data class NotificationResponse(
    val id: String,
    val memberId: String,
    val taskId: String,
    val title: String,
    val message: String,
    val createdAt: Long = 0,
    val read: Boolean = false
)

// ── Reward DTOs ────────────────────────────────────────────

@Immutable
@Serializable
data class RewardResponse(
    val id: String,
    val householdId: String,
    val title: String,
    val description: String = "",
    val cost: Int = 0,
    val icon: String = "🎁",
    val createdBy: String = "",
    val createdAt: Long = 0
)

@Serializable
data class RewardRedemption(
    val id: String,
    val rewardId: String,
    val memberId: String,
    val redeemedAt: Long = 0,
    val pointsSpent: Int = 0
)


