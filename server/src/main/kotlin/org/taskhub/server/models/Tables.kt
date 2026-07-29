package org.taskhub.server.models

import org.jetbrains.exposed.sql.Table

object Households : Table("household") {
    val id = text("id")
    val name = text("name")
    val inviteCode = text("invite_code")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object Members : Table("member") {
    val id = text("id")
    val householdId = text("household_id").references(Households.id)
    val displayName = text("display_name")
    val avatarUrl = text("avatar_url").nullable()
    val role = text("role").default("child")
    val totalPoints = integer("total_points").default(0)
    val joinedAt = long("joined_at")
    val leftAt = long("left_at").nullable()

    override val primaryKey = PrimaryKey(id)
}