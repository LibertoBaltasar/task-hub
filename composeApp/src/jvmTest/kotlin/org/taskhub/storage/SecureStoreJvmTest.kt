package org.taskhub.storage

import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Complementa [SecureStoreTest] (contrato común put/get/remove) con el caso
 * de fallo de descifrado REAL — solo posible desde `jvmTest` porque requiere
 * tocar el almacenamiento subyacente de la implementación JVM directamente
 * (`java.util.prefs.Preferences`, no expuesto por la interfaz común
 * [SecureStore]) para simular un ciphertext corrupto, en vez de confiar en
 * que `putString` siempre produzca uno válido (panel v7, #29 — hueco de
 * cobertura identificado por el panel de expertos, confirmado cubrible sin
 * mocks nuevos reutilizando el patrón de [SecureStoreTest]).
 */
class SecureStoreJvmTest {

    private val rawPrefs = Preferences.userRoot().node("org/taskhub/secure")

    @Test
    fun get_withCorruptedCiphertext_returnsNullInsteadOfThrowing() {
        val store = createSecureStore()
        val key = "corrupt_ciphertext_test_key"
        store.putString(key, "valid-value-before-corruption")

        // Tamper con el valor cifrado directamente en el almacenamiento
        // subyacente — un ciphertext corrupto (o con el tag GCM roto) hace
        // que Cipher.doFinal lance AEADBadTagException real, no una excepción
        // simulada.
        rawPrefs.put(key, "esto-no-es-un-ciphertext-AES-GCM-valido==")

        assertNull(store.getString(key))

        rawPrefs.remove(key)
    }

    @Test
    fun get_withNonBase64Garbage_returnsNullInsteadOfThrowing() {
        val store = createSecureStore()
        val key = "non_base64_test_key"
        store.putString(key, "another-valid-value")

        // Un valor que ni siquiera decodifica como Base64 debe fallar igual
        // de limpio (IllegalArgumentException de Base64.getDecoder, también
        // capturada por el catch genérico de getString).
        rawPrefs.put(key, "%%%not-base64%%%")

        assertNull(store.getString(key))

        rawPrefs.remove(key)
    }
}
