/**
 * Reglas puras (sin I/O) de gobierno del hogar: quién hereda el rol de owner
 * cuando el propietario actual desaparece. Usado por [FirestoreRepository]
 * y/o [HouseholdRepository] antes de escribir el nuevo owner en Firestore.
 */
package org.taskhub.network

import org.taskhub.network.models.MemberResponse

/**
 * Reglas puras de gobierno del hogar (transferencia de propiedad). Sin I/O —
 * testable directamente en `commonTest`.
 */
object HouseholdRules {

    /**
     * Candidato a heredar el rol owner de un hogar compartido cuando el owner
     * actual se borra la cuenta o abandona el hogar — sin esto,
     * `households/{hid}` queda sin nadie que pase `isOwner(hid)` en
     * `firestore.rules` para siempre (panel v4, Experto 2 hallazgo #6 ALTO).
     *
     * Orden de prioridad (decisión de producto de Liberto, ronda de deuda
     * aplicable 2026-09-12, punto B8):
     * 1. El miembro `role == "admin"` con cuenta vinculada más ANTIGUO (por
     *    [MemberResponse.joinedAt]) — un admin ya gestiona contenido
     *    sensible del hogar, así que hereda antes que un miembro "child" con
     *    más antigüedad.
     * 2. Si no queda ningún admin con cuenta vinculada, el miembro con
     *    cuenta vinculada más antiguo de cualquier rol (el caller lo asciende
     *    a admin al asignarle `ownerId` — ver `FirestoreRepository.leaveHousehold`).
     * 3. `null` si ningún miembro restante tiene cuenta vinculada: `isOwner`/
     *    `isAdminMember` en las reglas de Firestore exigen que el UID
     *    autenticado coincida con el documento de miembro, así que un perfil
     *    "hijo/a" sin cuenta vinculada nunca podría autenticarse como owner
     *    — el hogar queda sin owner operable (limitación conocida,
     *    documentada en el caller).
     */
    fun resolveOwnerSuccessor(remainingMembers: List<MemberResponse>): MemberResponse? {
        val withAccount = remainingMembers.filter { it.userId != null }
        val admins = withAccount.filter { it.role == "admin" }
        return admins.minByOrNull { it.joinedAt } ?: withAccount.minByOrNull { it.joinedAt }
    }
}
