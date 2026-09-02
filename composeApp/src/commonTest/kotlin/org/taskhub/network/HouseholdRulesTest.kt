package org.taskhub.network

import org.taskhub.network.models.MemberResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HouseholdRulesTest {

    private fun member(
        id: String,
        joinedAt: Long,
        userId: String? = id,
        role: String = "child"
    ) = MemberResponse(
        id = id,
        householdId = "hid",
        displayName = id,
        role = role,
        joinedAt = joinedAt,
        userId = userId
    )

    @Test
    fun resolveOwnerSuccessor_picksOldestWithLinkedAccount() {
        val members = listOf(
            member("m1", joinedAt = 300),
            member("m2", joinedAt = 100),
            member("m3", joinedAt = 200)
        )
        assertEquals("m2", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    @Test
    fun resolveOwnerSuccessor_ignoresMembersWithoutAccount() {
        val members = listOf(
            member("child-oldest", joinedAt = 50, userId = null),
            member("adult", joinedAt = 200, userId = "uid-adult")
        )
        assertEquals("adult", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    @Test
    fun resolveOwnerSuccessor_noneWithAccount_returnsNull() {
        val members = listOf(
            member("child-1", joinedAt = 50, userId = null),
            member("child-2", joinedAt = 100, userId = null)
        )
        assertNull(HouseholdRules.resolveOwnerSuccessor(members))
    }

    @Test
    fun resolveOwnerSuccessor_emptyList_returnsNull() {
        assertNull(HouseholdRules.resolveOwnerSuccessor(emptyList()))
    }
}
