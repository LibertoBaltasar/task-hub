package org.taskhub.network

import org.taskhub.network.models.MemberResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [MemberResolutionRules] decide a qué miembro EXISTENTE se atribuye el
 * usuario actual — la rama de [MemberRepository.resolveCurrentMemberUncached]
 * marcada como peligrosa en su propio comentario (panel 2026-09-11: un
 * fallback mal acotado podía hacer que un usuario heredara en silencio la
 * identidad de OTRO miembro real). Extraída a función pura para poder
 * testear justo ese caso sin mocks de red (ronda de deuda aplicable
 * 2026-09-12, punto C17).
 */
class MemberResolutionRulesTest {

    private fun member(id: String, userId: String? = id) = MemberResponse(
        id = id,
        householdId = "hid",
        displayName = id,
        role = "child",
        userId = userId
    )

    /** Coincidencia directa por identidad: se devuelve ese miembro, aunque haya otros sin cuenta. */
    @Test
    fun resolveExistingMemberId_matchesByIdentity() {
        val members = listOf(
            member(id = "child-no-account", userId = null),
            member(id = "me", userId = "uid-me")
        )

        val result = MemberResolutionRules.resolveExistingMemberId(members, identities = listOf("uid-me"))

        assertEquals("me", result)
    }

    /** Sin coincidencia directa, cae al primer miembro SIN cuenta vinculada (onboarding típico). */
    @Test
    fun resolveExistingMemberId_fallsBackToFirstUnclaimedProfile() {
        val members = listOf(
            member(id = "other-account", userId = "uid-other"),
            member(id = "child-1", userId = null),
            member(id = "child-2", userId = null)
        )

        val result = MemberResolutionRules.resolveExistingMemberId(members, identities = listOf("uid-me"))

        assertEquals("child-1", result)
    }

    /**
     * El bug que esta función fija: sin coincidencia directa Y sin ningún
     * perfil "child" sin reclamar, NUNCA debe devolver el miembro de OTRA
     * identidad real (antes `members.first()` sin condición robaba esa
     * identidad en silencio) — debe devolver `null` para que el caller cree
     * un miembro nuevo en vez de adivinar.
     */
    @Test
    fun resolveExistingMemberId_neverStealsAnotherRealIdentity() {
        val members = listOf(
            member(id = "someone-else", userId = "uid-someone-else")
        )

        val result = MemberResolutionRules.resolveExistingMemberId(members, identities = listOf("uid-me"))

        assertNull(result)
    }

    /** Hogar sin ningún miembro: null, el caller crea uno nuevo. */
    @Test
    fun resolveExistingMemberId_noMembers_returnsNull() {
        assertNull(MemberResolutionRules.resolveExistingMemberId(emptyList(), identities = listOf("uid-me")))
    }

    /** El usuario puede tener varias identidades (Google + anónima); cualquiera de ellas cuenta como match. */
    @Test
    fun resolveExistingMemberId_matchesAnyOfMultipleIdentities() {
        val members = listOf(member(id = "me", userId = "uid-anonymous"))

        val result = MemberResolutionRules.resolveExistingMemberId(
            members,
            identities = listOf("uid-google", "uid-anonymous")
        )

        assertEquals("me", result)
    }
}
