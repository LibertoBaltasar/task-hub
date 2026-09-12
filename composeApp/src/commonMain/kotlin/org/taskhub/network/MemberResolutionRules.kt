// Lógica pura (sin I/O) de resolución de "quién es el miembro actual" dentro
// de un hogar — usada por [MemberRepository.resolveCurrentMemberUncached].
// Extraída para poder testear la rama peligrosa (el fallback que decide a
// qué miembro EXISTENTE atribuir al usuario actual) sin mocks de red (ronda
// de deuda aplicable 2026-09-12, punto C17).
package org.taskhub.network

import org.taskhub.network.models.MemberResponse

/**
 * Reglas puras de resolución de miembro actual. Sin I/O — testable
 * directamente en `commonTest`.
 */
object MemberResolutionRules {

    /**
     * Resuelve el ID del miembro EXISTENTE que representa al usuario actual,
     * o `null` si no hay ninguno (el caller debe crear uno nuevo — paso 3 de
     * [org.taskhub.network.MemberRepository.resolveCurrentMemberUncached],
     * fuera de esta función por requerir I/O).
     *
     * Orden de preferencia:
     * 1. El miembro cuyo [MemberResponse.userId] coincide con cualquiera de
     *    [identities] del usuario actual (ver
     *    [org.taskhub.network.FirestoreClient.currentUserIdentities]).
     * 2. El primer miembro SIN cuenta vinculada (`userId == null`, perfil
     *    "child" del onboarding típico) — NUNCA uno cuyo `userId` ya
     *    pertenece a OTRA identidad real: antes se devolvía `members.first()`
     *    sin condición, así que un usuario cuya identidad local dejara de
     *    coincidir con ningún miembro heredaba en silencio la identidad de
     *    OTRO miembro real, sin ningún error visible (panel 2026-09-11,
     *    IMPORTANTE — bug que esta función fija mediante el filtro
     *    `it.userId == null` explícito).
     */
    fun resolveExistingMemberId(members: List<MemberResponse>, identities: List<String>): String? {
        members.firstOrNull { it.userId != null && it.userId in identities }?.let { return it.id }
        members.firstOrNull { it.userId == null }?.let { return it.id }
        return null
    }
}
