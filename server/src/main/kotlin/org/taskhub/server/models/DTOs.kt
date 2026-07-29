package org.taskhub.server.models

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
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class HouseholdCreatedResponse(
    val id: String,
    val name: String,
    val inviteCode: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class MemberResponse(
    val id: String,
    val householdId: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val totalPoints: Int,
    val joinedAt: Long
)

@Serializable
data class ErrorResponse(val error: String)