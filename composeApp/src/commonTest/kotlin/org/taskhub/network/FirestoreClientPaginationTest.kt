package org.taskhub.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [shouldFetchNextPage] decide si [listAllDocuments] sigue paginando —
 * pieza nueva de la tarjeta kanban "Paginación getMessages/getNotifications"
 * (2026-09-13) que sí se puede testear sin I/O real, igual que
 * [isTransientReadFailure] en [FirestoreClientRetryTest].
 */
class FirestoreClientPaginationTest {

    @Test
    fun shouldFetchNextPage_sinPageToken_paraSinImportarElLimite() {
        assertFalse(shouldFetchNextPage(pageToken = null, documentsSoFar = 0, limit = null))
        assertFalse(shouldFetchNextPage(pageToken = null, documentsSoFar = 5, limit = 10))
    }

    @Test
    fun shouldFetchNextPage_conPageTokenYSinLimite_sigue() {
        assertTrue(shouldFetchNextPage(pageToken = "tok", documentsSoFar = 1_000_000, limit = null))
    }

    @Test
    fun shouldFetchNextPage_conPageTokenYPorDebajoDelLimite_sigue() {
        assertTrue(shouldFetchNextPage(pageToken = "tok", documentsSoFar = 5, limit = 10))
    }

    @Test
    fun shouldFetchNextPage_conPageTokenYYaAlcanzadoElLimite_para() {
        assertFalse(shouldFetchNextPage(pageToken = "tok", documentsSoFar = 10, limit = 10))
    }

    @Test
    fun shouldFetchNextPage_conPageTokenYPorEncimaDelLimite_para() {
        assertFalse(shouldFetchNextPage(pageToken = "tok", documentsSoFar = 11, limit = 10))
    }
}
