package org.taskhub.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "org.taskhub.secure"

/**
 * Ver [SecureStore]. Respaldado por el Keychain de iOS (`kSecClassGenericPassword`),
 * accedido con las funciones C `SecItem*` de Security.framework y un
 * `NSMutableDictionary` como query — Foundation/CoreFoundation son "toll-free
 * bridged" en Kotlin/Native, así que un `NSDictionary` se castea directamente
 * a `CFDictionaryRef` (`as CFDictionaryRef`) sin conversión manual.
 *
 * ⚠️ IMPORTANTE: este archivo NO se pudo compilar ni ejecutar en el entorno
 * donde se escribió (sin toolchain de Xcode/macOS — misma limitación que el
 * resto del proyecto, ver docs/review-panel-expertos-v3-2026-09-01.md).
 * Verificar con un build nativo de iOS antes de publicar.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createSecureStore(): SecureStore = KeychainSecureStore()

@OptIn(ExperimentalForeignApi::class)
private class KeychainSecureStore : SecureStore {

    private fun baseQuery(key: String): NSMutableDictionary {
        val dict = NSMutableDictionary()
        dict.setObject(kSecClassGenericPassword as NSString, forKey = kSecClass as NSString)
        dict.setObject(KEYCHAIN_SERVICE, forKey = kSecAttrService as NSString)
        dict.setObject(key, forKey = kSecAttrAccount as NSString)
        return dict
    }

    override fun getString(key: String): String? = memScoped {
        val query = baseQuery(key)
        query.setObject(true, forKey = kSecReturnData as NSString)
        query.setObject(kSecMatchLimitOne as NSString, forKey = kSecMatchLimit as NSString)

        val resultVar = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, resultVar.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = resultVar.value as? NSData ?: return@memScoped null
        NSString.create(data, NSUTF8StringEncoding) as? String
    }

    override fun putString(key: String, value: String) {
        // Borra cualquier valor previo primero: así no hace falta distinguir
        // "crear" de "actualizar" (SecItemAdd falla si el ítem ya existe).
        remove(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val attributes = baseQuery(key)
        attributes.setObject(data, forKey = kSecValueData as NSString)
        SecItemAdd(attributes as CFDictionaryRef, null)
    }

    override fun remove(key: String) {
        SecItemDelete(baseQuery(key) as CFDictionaryRef)
    }
}
