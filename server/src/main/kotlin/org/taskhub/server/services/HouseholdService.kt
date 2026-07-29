package org.taskhub.server.services

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.taskhub.server.models.HouseholdCreatedResponse
import org.taskhub.server.models.HouseholdResponse
import org.taskhub.server.models.Households
import java.util.UUID
import kotlin.random.Random

class HouseholdService {

    fun create(name: String): HouseholdCreatedResponse = transaction {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()

        Households.insert {
            it[Households.id] = id
            it[Households.name] = name
            it[Households.inviteCode] = inviteCode
            it[Households.createdAt] = now
            it[Households.updatedAt] = now
        }

        HouseholdCreatedResponse(
            id = id,
            name = name,
            inviteCode = inviteCode,
            createdAt = now,
            updatedAt = now
        )
    }

    fun getById(id: String): HouseholdResponse? = transaction {
        Households.selectAll().where { Households.id eq id }.singleOrNull()?.let { row ->
            HouseholdResponse(
                id = row[Households.id],
                name = row[Households.name],
                inviteCode = row[Households.inviteCode],
                createdAt = row[Households.createdAt],
                updatedAt = row[Households.updatedAt]
            )
        }
    }

    fun join(inviteCode: String): HouseholdResponse? = transaction {
        Households.selectAll().where { Households.inviteCode eq inviteCode }.singleOrNull()?.let { row ->
            HouseholdResponse(
                id = row[Households.id],
                name = row[Households.name],
                inviteCode = row[Households.inviteCode],
                createdAt = row[Households.createdAt],
                updatedAt = row[Households.updatedAt]
            )
        }
    }

    fun exists(id: String): Boolean = transaction {
        Households.selectAll().where { Households.id eq id }.count() > 0
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}