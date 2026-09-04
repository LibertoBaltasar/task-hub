package org.taskhub.network

import org.taskhub.network.models.TaskAssignmentResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AssignmentCompletionRules] cubre el cierre de asignaciones hermanas de
 * `FirestoreRepository.completeAssignment` — antes sin ningún test de
 * integración posible por depender de red (panel de revisión 2026-09-03/04,
 * Experto 13, hueco CRÍTICO; panel v7, #31: extraída a función pura en vez
 * de añadir `ktor-client-mock` como dependencia nueva).
 */
class AssignmentCompletionRulesTest {

    private fun assignment(
        id: String,
        memberId: String = "member-1",
        status: String = "assigned"
    ) = TaskAssignmentResponse(
        id = id,
        taskId = "task-1",
        memberId = memberId,
        status = status
    )

    @Test
    fun siblingsToClose_excludesTheJustCompletedAssignment() {
        val all = listOf(
            assignment(id = "a1", status = "completed"), // ya completada por completeAssignment antes de este paso
            assignment(id = "a2", status = "assigned")
        )

        val siblings = AssignmentCompletionRules.siblingsToClose(all, completedAssignmentId = "a1")

        assertEquals(listOf(assignment(id = "a2", status = "assigned")), siblings)
    }

    @Test
    fun siblingsToClose_excludesAssignmentsThatAreNotAssigned() {
        val all = listOf(
            assignment(id = "a1", status = "assigned"),
            assignment(id = "a2", status = "completed"),
            assignment(id = "a3", status = "cancelled")
        )

        val siblings = AssignmentCompletionRules.siblingsToClose(all, completedAssignmentId = "a1")

        assertTrue(siblings.isEmpty())
    }

    @Test
    fun siblingsToClose_includesAllOtherAssignedSiblingsRegardlessOfMember() {
        val all = listOf(
            assignment(id = "a1", memberId = "alice", status = "assigned"),
            assignment(id = "a2", memberId = "bob", status = "assigned"),
            assignment(id = "a3", memberId = "carol", status = "assigned")
        )

        val siblings = AssignmentCompletionRules.siblingsToClose(all, completedAssignmentId = "a1")

        assertEquals(setOf("a2", "a3"), siblings.map { it.id }.toSet())
    }

    @Test
    fun siblingsToClose_withNoOtherAssignments_returnsEmpty() {
        val all = listOf(assignment(id = "a1", status = "assigned"))

        val siblings = AssignmentCompletionRules.siblingsToClose(all, completedAssignmentId = "a1")

        assertTrue(siblings.isEmpty())
    }

    @Test
    fun siblingsToClose_withEmptyAssignmentList_returnsEmpty() {
        assertTrue(AssignmentCompletionRules.siblingsToClose(emptyList(), completedAssignmentId = "a1").isEmpty())
    }
}
