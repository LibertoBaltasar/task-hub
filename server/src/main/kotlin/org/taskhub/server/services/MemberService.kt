package org.taskhub.server.services

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.taskhub.server.models.MemberResponse
import org.taskhub.server.models.Members
import java.util.UUID

class MemberService {

    fun listByHousehold(householdId: String): List<MemberResponse> = transaction {
        Members.selectAll()
            .where { (Members.householdId eq householdId) and (Members.leftAt.isNull()) }
            .map { row -> row.toMemberResponse() }
    }

    fun create(
        householdId: String,
        displayName: String,
        role: String = "child",
        avatarUrl: String? = null
    ): MemberResponse = transaction {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = UUID.randomUUID().toString()

        Members.insert {
            it[Members.id] = id
            it[Members.householdId] = householdId
            it[Members.displayName] = displayName
            it[Members.role] = role
            it[Members.avatarUrl] = avatarUrl
            it[Members.totalPoints] = 0
            it[Members.joinedAt] = now
        }

        MemberResponse(
            id = id,
            householdId = householdId,
            displayName = displayName,
            role = role,
            avatarUrl = avatarUrl,
            totalPoints = 0,
            joinedAt = now
        )
    }

    fun leave(householdId: String, memberId: String): Boolean = transaction {
        val now = Clock.System.now().toEpochMilliseconds()
        Members.update({
            (Members.householdId eq householdId) and
            (Members.id eq memberId) and
            (Members.leftAt.isNull())
        }) {
            it[leftAt] = now
        } > 0
    }

    fun exists(householdId: String, memberId: String): Boolean = transaction {
        Members.selectAll()
            .where { (Members.householdId eq householdId) and (Members.id eq memberId) and (Members.leftAt.isNull()) }
            .count() > 0
    }

    private fun ResultRow.toMemberResponse() = MemberResponse(
        id = this[Members.id],
        householdId = this[Members.householdId],
        displayName = this[Members.displayName],
        avatarUrl = this[Members.avatarUrl],
        role = this[Members.role],
        totalPoints = this[Members.totalPoints],
        joinedAt = this[Members.joinedAt]
    )
}