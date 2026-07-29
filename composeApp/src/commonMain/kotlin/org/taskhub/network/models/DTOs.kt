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
    val joinedAt: Long = 0
)

@Serializable
data class ErrorResponse(val error: String)
