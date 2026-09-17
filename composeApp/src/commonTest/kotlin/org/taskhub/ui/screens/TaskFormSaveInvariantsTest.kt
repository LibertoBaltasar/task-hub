package org.taskhub.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regresión 2026-09-17: en el formulario de crear/editar tarea, la
 * penalización por retraso y la rotación semanal se podían configurar (y
 * guardar) sin fecha límite / con frecuencia distinta de semanal
 * respectivamente — estados sin sentido de negocio que ahora la UI ya ni
 * siquiera muestra (ver `resolvedPenaltyMode`/`resolvedRotation` en
 * `CreateTaskScreen.kt`, usadas también desde `EditTaskScreen.kt`). Estas
 * pruebas cubren la invariante de guardado en sí, no la visibilidad de UI
 * (no hay infraestructura de Compose UI testing en este módulo).
 */
class TaskFormSaveInvariantsTest {

    @Test
    fun `penalizacion se descarta al guardar sin fecha limite aunque hasPenalty siga activo`() {
        val result = resolvedPenaltyMode(hasPenalty = true, hasDeadline = false, penaltyMode = "fixed")
        assertNull(result)
    }

    @Test
    fun `penalizacion se guarda cuando hay fecha limite y esta activada`() {
        val result = resolvedPenaltyMode(hasPenalty = true, hasDeadline = true, penaltyMode = "percentage")
        assertEquals("percentage", result)
    }

    @Test
    fun `penalizacion desactivada no se guarda aunque haya fecha limite`() {
        val result = resolvedPenaltyMode(hasPenalty = false, hasDeadline = true, penaltyMode = "fixed")
        assertNull(result)
    }

    @Test
    fun `rotacion se descarta al guardar si la frecuencia no es semanal aunque hasRotation siga activo`() {
        val slots = mapOf(1 to "member-1", 2 to "member-2")
        val result = resolvedRotation(hasRotation = true, frequency = "daily", rotationSlots = slots)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rotacion se guarda con frecuencia semanal y slots asignados`() {
        val slots = mapOf(1 to "member-1", 2 to "", 3 to "member-3")
        val result = resolvedRotation(hasRotation = true, frequency = "weekly", rotationSlots = slots)
        assertEquals(setOf(1, 3), result.map { it.dayOfWeek }.toSet())
        assertTrue(result.none { it.memberId.isBlank() })
    }

    @Test
    fun `rotacion desactivada no se guarda aunque la frecuencia sea semanal`() {
        val slots = mapOf(1 to "member-1")
        val result = resolvedRotation(hasRotation = false, frequency = "weekly", rotationSlots = slots)
        assertTrue(result.isEmpty())
    }
}
