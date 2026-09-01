package org.taskhub.storage

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val KEY_FILE_NAME = ".taskhub_secure_key"
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128

/**
 * Ver [SecureStore]. Desktop no tiene un keychain de sistema accesible sin
 * una dependencia nativa adicional (a diferencia de Android/iOS), así que el
 * "equivalente" aquí es AES-256-GCM con una clave generada una vez y guardada
 * en un fichero propio (`~/.taskhub/.taskhub_secure_key`) con permisos
 * restringidos al usuario del sistema operativo — el mismo nivel de
 * protección que ofrece cualquier keychain de escritorio (aislamiento por
 * cuenta de SO), sin añadir una librería nueva. El valor cifrado se guarda
 * en `java.util.prefs.Preferences` (igual que el resto de `Settings()` en
 * JVM), pero en un nodo separado.
 */
actual fun createSecureStore(): SecureStore = JvmSecureStore()

private class JvmSecureStore : SecureStore {
    private val prefs = Preferences.userRoot().node("org/taskhub/secure")
    private val secretKey: SecretKeySpec by lazy { loadOrCreateKey() }

    override fun getString(key: String): String? {
        val encoded = prefs.get(key, null) ?: return null
        return try {
            decrypt(encoded)
        } catch (_: Exception) {
            null
        }
    }

    override fun putString(key: String, value: String) {
        prefs.put(key, encrypt(value))
    }

    override fun remove(key: String) {
        prefs.remove(key)
    }

    private fun encrypt(plain: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun loadOrCreateKey(): SecretKeySpec {
        val dir = File(System.getProperty("user.home"), ".taskhub")
        if (!dir.exists()) dir.mkdirs()
        val keyFile = File(dir, KEY_FILE_NAME)
        if (!keyFile.exists()) {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            keyFile.writeBytes(key)
            // Best-effort: en filesystems sin permisos POSIX (p.ej. FAT) esto
            // no hace nada, pero no debe romper la creación del fichero.
            try {
                keyFile.setReadable(false, false)
                keyFile.setReadable(true, true)
                keyFile.setWritable(false, false)
                keyFile.setWritable(true, true)
            } catch (_: Exception) {
                // Ignorado a propósito — ver comentario anterior.
            }
        }
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }
}
