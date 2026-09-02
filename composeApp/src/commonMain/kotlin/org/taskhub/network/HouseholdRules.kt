package org.taskhub.network

import org.taskhub.network.models.MemberResponse

/**
 * Reglas puras de gobierno del hogar (transferencia de propiedad). Sin I/O —
 * testable directamente en `commonTest`.
 */
object HouseholdRules {

    /**
     * Miembro más antiguo (por [MemberResponse.joinedAt]) entre [remainingMembers]
     * con cuenta vinculada ([MemberResponse.userId] no nulo), candidato a
     * heredar el rol owner de un hogar compartido cuando el owner actual se
     * borra la cuenta o abandona el hogar — sin esto, `households/{hid}`
     * queda sin nadie que pase `isOwner(hid)` en `firestore.rules` para
     * siempre (panel v4, Experto 2 hallazgo #6 ALTO).
     *
     * Solo se consideran miembros con [MemberResponse.userId]: `isOwner`/
     * `isAdminMember` en las reglas de Firestore exigen que el UID
     * autenticado coincida con el documento de miembro, así que un perfil
     * "hijo/a" sin cuenta vinculada nunca podría autenticarse como owner.
     * Devuelve null si ninguno de los restantes tiene cuenta vinculada.
     */
    fun resolveOwnerSuccessor(remainingMembers: List<MemberResponse>): MemberResponse? =
        remainingMembers.filter { it.userId != null }.minByOrNull { it.joinedAt }
}
