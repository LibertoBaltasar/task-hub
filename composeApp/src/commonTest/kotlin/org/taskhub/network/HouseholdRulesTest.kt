package org.taskhub.network

import org.taskhub.network.models.MemberResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [HouseholdRules] decide quién hereda la propiedad (`ownerId`) de un hogar
 * cuando el owner actual lo abandona o elimina su cuenta. Solo un miembro
 * vinculado a una cuenta real (`userId != null`) puede ser sucesor — un
 * perfil "hijo/a" sin cuenta no puede quedar como propietario del hogar.
 */
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

    /** Entre varios candidatos con cuenta, el sucesor es el que lleva más tiempo en el hogar (más antiguo). */
    @Test
    fun resolveOwnerSuccessor_picksOldestWithLinkedAccount() {
        val members = listOf(
            member("m1", joinedAt = 300),
            member("m2", joinedAt = 100),
            member("m3", joinedAt = 200)
        )
        assertEquals("m2", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    /** Un perfil "hijo/a" sin cuenta (userId null) nunca es candidato a sucesor, aunque sea más antiguo. */
    @Test
    fun resolveOwnerSuccessor_ignoresMembersWithoutAccount() {
        val members = listOf(
            member("child-oldest", joinedAt = 50, userId = null),
            member("adult", joinedAt = 200, userId = "uid-adult")
        )
        assertEquals("adult", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    /** Si nadie en el hogar tiene cuenta vinculada, no hay sucesor posible: null. */
    @Test
    fun resolveOwnerSuccessor_noneWithAccount_returnsNull() {
        val members = listOf(
            member("child-1", joinedAt = 50, userId = null),
            member("child-2", joinedAt = 100, userId = null)
        )
        assertNull(HouseholdRules.resolveOwnerSuccessor(members))
    }

    /** Caso límite defensivo: hogar sin miembros no debe romper el cálculo. */
    @Test
    fun resolveOwnerSuccessor_emptyList_returnsNull() {
        assertNull(HouseholdRules.resolveOwnerSuccessor(emptyList()))
    }
}
