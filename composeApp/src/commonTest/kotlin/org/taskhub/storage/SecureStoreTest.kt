package org.taskhub.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifica el contrato de [SecureStore] (put/get/remove) contra la
 * implementación real de la plataforma que ejecute el test — en este
 * repo, la única que corre en CI/local sin toolchain nativo es la de JVM
 * ([org.taskhub.storage] `SecureStore.jvm.kt`, AES-256-GCM). Android/iOS no
 * se pueden ejercitar así (requieren instrumentación/Xcode), pero comparten
 * la misma interfaz — ver hallazgo de seguridad B1
 * (docs/review-panel-expertos-v3-2026-09-01.md, Experto 9).
 */
class SecureStoreTest {

    /** Un valor guardado con [SecureStore.putString] se recupera intacto con [SecureStore.getString]. */
    @Test
    fun putThenGet_returnsSameValue() {
        val store = createSecureStore()
        store.putString("test_key", "s3cr3t-value")

        assertEquals("s3cr3t-value", store.getString("test_key"))
    }

    /** Leer una key que nunca se escribió devuelve `null` en vez de lanzar. */
    @Test
    fun get_withoutPut_returnsNull() {
        val store = createSecureStore()

        assertNull(store.getString("never_written_key"))
    }

    /** [SecureStore.remove] borra el valor: una lectura posterior vuelve a devolver `null`. */
    @Test
    fun remove_clearsValue() {
        val store = createSecureStore()
        store.putString("removable_key", "value")

        store.remove("removable_key")

        assertNull(store.getString("removable_key"))
    }

    /** Escribir dos veces la misma key reemplaza el valor anterior (no lo acumula). */
    @Test
    fun put_overwritesPreviousValue() {
        val store = createSecureStore()
        store.putString("overwrite_key", "first")
        store.putString("overwrite_key", "second")

        assertEquals("second", store.getString("overwrite_key"))
    }
}
