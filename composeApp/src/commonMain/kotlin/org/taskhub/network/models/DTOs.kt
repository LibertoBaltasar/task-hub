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
    val updatedAt: Long = 0
)

@Serializable
data class MemberResponse(
    val id: String,
    val householdId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: String,
    val totalPoints: Int = 0,
    val joinedAt: Long = 0,
    val userId: String? = null
)

@Serializable
data class ErrorResponse(val error: String)

// ── Task DTOs ────────────────────────────────────────────

@Serializable
data class TaskResponse(
    val id: String,
    val householdId: String,
    val createdBy: String,
    val title: String,
    val description: String = "",
    val points: Int = 10,
    val frequency: String = "once", // once | daily | weekly | monthly
    val recurrenceDays: List<Int> = emptyList(), // 1=Monday..7=Sunday
    val tags: List<String> = emptyList(),
    val penaltyMode: String? = null, // "fixed" | "percentage" | null
    val penaltyValue: Int = 0,
    val penaltyInterval: String = "day", // day | week | month
    val penaltyMax: Int = 0,
    val dueDate: Long = 0, // epoch millis — for "once" tasks; 0 = no specific date
    val lastCompletedDate: Long? = null, // epoch millis — last time this task was completed
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class TaskAssignmentResponse(
    val id: String,
    val taskId: String,
    val memberId: String,
    val mandatory: Boolean = false,
    val dueDate: Long = 0, // epoch millis
    val status: String = "assigned", // assigned | completed | overdue | penalized
    val completedAt: Long? = null,
    val pointsAwarded: Int? = null,
    val onTime: Boolean? = null,
    val assignedAt: Long = 0
)


