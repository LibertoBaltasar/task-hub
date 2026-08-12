package org.taskhub.network.models

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
    val lastStreakDate: Long = 0
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
    /** Rotación diaria de asignados: quién le toca cada día de la semana. */
    val assignmentRotation: List<AssignmentSlot> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * Asignación de una tarea a un miembro.
 * Una tarea puede tener 0..N asignaciones (una por miembro).
 */
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
    val assignedAt: Long = 0
)

// ── Comments DTO ─────────────────────────────────────────

@Serializable
data class CommentResponse(
    val id: String,
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


