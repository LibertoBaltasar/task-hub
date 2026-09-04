package org.taskhub.network

import org.taskhub.network.models.TaskAssignmentResponse

/**
 * Regla pura de "qué asignaciones hermanas cerrar" al completar una — extraída
 * de [FirestoreRepository.completeAssignment] para poder testearla sin red
 * (panel de revisión 2026-09-03/04, Experto 13: hueco CRÍTICO, solo cubrible
 * con `ktor-client-mock` o extrayendo la lógica a función pura; panel v7,
 * #31, opción elegida).
 */
object AssignmentCompletionRules {

    /**
     * De todas las asignaciones de la tarea ([allAssignments]), cuáles son
     * "hermanas" del mismo ciclo que deben marcarse completadas (con 0
     * puntos, ver KDoc de `completeAssignment`) tras completar
     * [completedAssignmentId]: todas las que sigan "assigned" salvo la que
     * ya se completó explícitamente.
     */
    fun siblingsToClose(
        allAssignments: List<TaskAssignmentResponse>,
        completedAssignmentId: String
    ): List<TaskAssignmentResponse> =
        allAssignments.filter { it.id != completedAssignmentId && it.status == "assigned" }
}
