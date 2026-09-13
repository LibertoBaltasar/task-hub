// Capa de persistencia (storage/), implementación web (wasmJs) del contrato
// [SecureStore] (`actual` de la `expect fun createSecureStore()` común).

package org.taskhub.storage

import kotlinx.browser.localStorage

private const val KEY_PREFIX = "taskhub_secure_"

/**
 * Ver [SecureStore]. El navegador no expone un keychain de sistema: se usa
 * `localStorage` sin cifrado real como placeholder (hueco documentado en el
 * resumen del encargo web — pendiente de una solución equivalente a
 * EncryptedSharedPreferences/Keychain/AES-GCM-JVM antes de confiar tokens
 * sensibles a este almacén en producción web).
 */
actual fun createSecureStore(): SecureStore = WasmJsSecureStore()

private class WasmJsSecureStore : SecureStore {
    override fun getString(key: String): String? = localStorage.getItem(KEY_PREFIX + key)

    override fun putString(key: String, value: String) {
        localStorage.setItem(KEY_PREFIX + key, value)
    }

    override fun remove(key: String) {
        localStorage.removeItem(KEY_PREFIX + key)
    }
}
