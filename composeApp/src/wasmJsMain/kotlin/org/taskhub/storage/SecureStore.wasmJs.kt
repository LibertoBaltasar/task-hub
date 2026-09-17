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
    // `localStorage` puede lanzar (SecurityError con cookies/storage bloqueado
    // por el usuario o política del navegador, QuotaExceededError al escribir)
    // — sin capturarlo, un storage no disponible rompía la lectura de ajustes
    // en el arranque de `App()` antes de montar nada (panel v12, Web).
    override fun getString(key: String): String? =
        try {
            localStorage.getItem(KEY_PREFIX + key)
        } catch (_: Throwable) {
            null
        }

    override fun putString(key: String, value: String) {
        try {
            localStorage.setItem(KEY_PREFIX + key, value)
        } catch (_: Throwable) {
            // Best-effort: sin storage disponible no hay dónde persistir la
            // sesión, pero no debe tumbar el flujo que la está guardando.
        }
    }

    override fun remove(key: String) {
        try {
            localStorage.removeItem(KEY_PREFIX + key)
        } catch (_: Throwable) {
            // Best-effort, mismo motivo que putString.
        }
    }
}
