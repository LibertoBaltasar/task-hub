package org.taskhub.storage

import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `SettingsStore.migrateLegacyToken` es `private`, así que se testea de
 * forma indirecta a través de [SettingsStore.getGoogleRefreshToken] /
 * [SettingsStore.getAnonymousRefreshToken] (sus únicos dos call sites) —
 * panel v4, Experto 13, hueco #6. Usa dobles de prueba (`FakeSettings`,
 * `FakeSecureStore`) en vez de `MapSettings`/una librería de mocks, que no
 * están entre las dependencias del proyecto.
 *
 * Los nombres de key (`taskhub_google_refresh_token`, etc.) están
 * hardcodeados aquí porque las constantes de [SettingsStore] son `private`
 * — coinciden con `SettingsStore.KEY_GOOGLE_REFRESH_TOKEN`/`KEY_ANON_REFRESH_TOKEN`.
 */
class SettingsStoreTest {

    private val keyGoogleRefreshToken = "taskhub_google_refresh_token"
    private val keyAnonRefreshToken = "taskhub_anon_refresh_token"

    private fun store(settings: FakeSettings, secureStore: FakeSecureStore) =
        SettingsStore(settings, lazy { secureStore })

    // ── getGoogleRefreshToken — migración de token legado ──────────

    @Test
    fun getGoogleRefreshToken_migratesLegacyPlainTextToken_toSecureStore() {
        val settings = FakeSettings(mutableMapOf(keyGoogleRefreshToken to "legacy-google-token"))
        val secureStore = FakeSecureStore()

        val token = store(settings, secureStore).getGoogleRefreshToken()

        assertEquals("legacy-google-token", token)
        assertEquals("legacy-google-token", secureStore.getString(keyGoogleRefreshToken))
        assertNull(settings.getStringOrNull(keyGoogleRefreshToken)) // el original se borra tras migrar
    }

    @Test
    fun getGoogleRefreshToken_alreadyMigrated_prefersSecureStoreWithoutTouchingSettings() {
        val settings = FakeSettings()
        val secureStore = FakeSecureStore(mutableMapOf(keyGoogleRefreshToken to "secure-google-token"))

        val token = store(settings, secureStore).getGoogleRefreshToken()

        assertEquals("secure-google-token", token)
        assertNull(settings.getStringOrNull(keyGoogleRefreshToken)) // no hubo migración: settings sigue vacío
    }

    @Test
    fun getGoogleRefreshToken_noValueAnywhere_returnsNullWithoutSideEffects() {
        val settings = FakeSettings()
        val secureStore = FakeSecureStore()

        val token = store(settings, secureStore).getGoogleRefreshToken()

        assertNull(token)
        assertEquals(0, secureStore.size)
    }

    @Test
    fun getGoogleRefreshToken_secureStoreCannotBeRead_fallsBackSilentlyToLegacyValue() {
        // Simula un valor cifrado ilegible (p.ej. clave rotada/corrupta): la
        // implementación real de SecureStore (ver SecureStore.jvm.kt)
        // atrapa la excepción de descifrado y devuelve null en vez de
        // propagarla — aquí se modela ese mismo contrato con
        // `simulateUnreadable`. SettingsStore no debe romperse con ese null:
        // debe caer de vuelta al valor legado en texto plano, si existe.
        val settings = FakeSettings(mutableMapOf(keyGoogleRefreshToken to "legacy-fallback-token"))
        val secureStore = FakeSecureStore(simulateUnreadable = true)

        val token = store(settings, secureStore).getGoogleRefreshToken()

        assertEquals("legacy-fallback-token", token)
    }

    @Test
    fun getGoogleRefreshToken_secureStoreCannotBeRead_andNoLegacyValue_returnsNullSilently() {
        val settings = FakeSettings()
        val secureStore = FakeSecureStore(simulateUnreadable = true)

        val token = store(settings, secureStore).getGoogleRefreshToken()

        assertNull(token)
    }

    // ── getAnonymousRefreshToken — mismo mecanismo, distinta key ──

    @Test
    fun getAnonymousRefreshToken_migratesLegacyPlainTextToken_toSecureStore() {
        val settings = FakeSettings(mutableMapOf(keyAnonRefreshToken to "legacy-anon-token"))
        val secureStore = FakeSecureStore()

        val token = store(settings, secureStore).getAnonymousRefreshToken()

        assertEquals("legacy-anon-token", token)
        assertEquals("legacy-anon-token", secureStore.getString(keyAnonRefreshToken))
        assertNull(settings.getStringOrNull(keyAnonRefreshToken))
    }

    @Test
    fun getAnonymousRefreshToken_noValueAnywhere_returnsNull() {
        val settings = FakeSettings()
        val secureStore = FakeSecureStore()

        assertNull(store(settings, secureStore).getAnonymousRefreshToken())
    }

    // ── getCalendarId/setCalendarId — mapa por hogar (panel v7, #30) ──

    @Test
    fun getCalendarId_withoutSetCalendarId_returnsNull() {
        val store = store(FakeSettings(), FakeSecureStore())

        assertNull(store.getCalendarId("household-1"))
    }

    @Test
    fun setCalendarId_thenGetCalendarId_returnsSameValue() {
        val store = store(FakeSettings(), FakeSecureStore())

        store.setCalendarId("household-1", "calendar-abc")

        assertEquals("calendar-abc", store.getCalendarId("household-1"))
    }

    @Test
    fun setCalendarId_forMultipleHouseholds_keepsThemIndependent() {
        val store = store(FakeSettings(), FakeSecureStore())

        store.setCalendarId("household-1", "calendar-abc")
        store.setCalendarId("household-2", "calendar-xyz")

        assertEquals("calendar-abc", store.getCalendarId("household-1"))
        assertEquals("calendar-xyz", store.getCalendarId("household-2"))
    }

    @Test
    fun setCalendarId_overwritesPreviousValueForSameHousehold() {
        val store = store(FakeSettings(), FakeSecureStore())

        store.setCalendarId("household-1", "calendar-old")
        store.setCalendarId("household-1", "calendar-new")

        assertEquals("calendar-new", store.getCalendarId("household-1"))
    }

    @Test
    fun getCalendarId_withCorruptedStoredJson_returnsNullInsteadOfThrowing() {
        // Simula un valor corrupto (p. ej. escrito por una versión anterior
        // incompatible del esquema) — getCalendarIdMap() atrapa el error de
        // parseo y cae a mapa vacío en vez de propagar la excepción.
        val settings = FakeSettings(mutableMapOf("taskhub_calendar_ids" to "{not-valid-json"))
        val store = store(settings, FakeSecureStore())

        assertNull(store.getCalendarId("household-1"))
    }
}

/** Doble de prueba mínimo de [SecureStore], sin cifrado real. */
private class FakeSecureStore(
    initial: MutableMap<String, String> = mutableMapOf(),
    private val simulateUnreadable: Boolean = false
) : SecureStore {
    private val map = initial

    val size: Int get() = map.size

    override fun getString(key: String): String? = if (simulateUnreadable) null else map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

/** Doble de prueba mínimo de [Settings], en memoria (sin persistencia real de plataforma). */
private class FakeSettings(initial: MutableMap<String, Any> = mutableMapOf()) : Settings {
    private val map = initial

    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size

    override fun clear() { map.clear() }
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)

    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int

    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long

    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String

    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float

    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double

    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}
