// Capa de persistencia (storage/): contrato multiplataforma para el
// almacén CIFRADO de credenciales sensibles. Cada plataforma (android/ios/
// jvm) aporta su propia implementación vía `expect`/`actual`.

package org.taskhub.storage

/**
 * Almacén cifrado minimalista para credenciales sensibles (hoy: los refresh
 * tokens de Firebase Auth — ver hallazgo de seguridad B1,
 * docs/review-panel-expertos-v3-2026-09-01.md, Experto 9). Deliberadamente
 * más pequeño que [com.russhwolf.settings.Settings] (solo String): no hace
 * falta cifrar el resto de preferencias (tema, idioma, notificaciones), y
 * limitar la superficie reduce el riesgo de una implementación nativa mal
 * hecha por plataforma.
 *
 * Implementado con `EncryptedSharedPreferences` (Jetpack Security, AES-256)
 * en Android, Keychain en iOS y cifrado AES-256-GCM con clave local
 * restringida al usuario del SO en JVM (desktop no tiene un keychain de
 * sistema accesible sin una dependencia nativa adicional).
 */
interface SecureStore {
    /** Devuelve el valor cifrado guardado bajo [key], o `null` si no existe o no se puede descifrar. */
    fun getString(key: String): String?

    /** Cifra [value] y lo guarda bajo [key] (sobrescribe cualquier valor previo). */
    fun putString(key: String, value: String)

    /** Borra el valor guardado bajo [key]. No falla si no existía. */
    fun remove(key: String)
}

/** Ver [SecureStore]. Una implementación por plataforma (android/ios/jvm). */
expect fun createSecureStore(): SecureStore
