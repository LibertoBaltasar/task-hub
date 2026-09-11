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

    /**
     * Un admin con cuenta hereda ANTES que un "child" con cuenta más antiguo
     * — decisión de producto (ronda de deuda aplicable 2026-09-12, punto B8):
     * el rol pesa más que la antigüedad.
     */
    @Test
    fun resolveOwnerSuccessor_prefersAdminOverOlderNonAdmin() {
        val members = listOf(
            member("child-oldest", joinedAt = 10, role = "child"),
            member("admin-newer", joinedAt = 500, role = "admin")
        )
        assertEquals("admin-newer", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    /** Entre varios admins con cuenta, el sucesor es el admin más antiguo. */
    @Test
    fun resolveOwnerSuccessor_amongAdmins_picksOldest() {
        val members = listOf(
            member("admin-1", joinedAt = 300, role = "admin"),
            member("admin-2", joinedAt = 100, role = "admin"),
            member("child-1", joinedAt = 50, role = "child")
        )
        assertEquals("admin-2", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    /** Sin ningún admin con cuenta, cae al miembro con cuenta más antiguo de cualquier rol. */
    @Test
    fun resolveOwnerSuccessor_noAdmins_fallsBackToOldestWithAccount() {
        val members = listOf(
            member("child-newer", joinedAt = 300, role = "child"),
            member("child-older", joinedAt = 100, role = "child"),
            member("no-account", joinedAt = 10, role = "child", userId = null)
        )
        assertEquals("child-older", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }

    /** Un admin SIN cuenta vinculada no es candidato — mismo motivo que cualquier otro rol sin userId. */
    @Test
    fun resolveOwnerSuccessor_adminWithoutAccount_isIgnored() {
        val members = listOf(
            member("admin-no-account", joinedAt = 10, role = "admin", userId = null),
            member("child-with-account", joinedAt = 200, role = "child")
        )
        assertEquals("child-with-account", HouseholdRules.resolveOwnerSuccessor(members)?.id)
    }
}
