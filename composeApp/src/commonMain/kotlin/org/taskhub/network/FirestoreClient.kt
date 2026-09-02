package org.taskhub.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP + autenticación Firebase de bajo nivel, extraído de
 * [FirestoreRepository] (ver docs/refactor-arquitectura-2026-08-31.md, punto 6,
 * fase 1). Sin lógica de dominio: solo transporte, gestión de tokens y parseo
 * de errores de la API REST de Firestore/Firebase Auth. Se inyecta por
 * composición en [FirestoreRepository] y, en fases futuras, en los repos de
 * dominio que se extraigan de él.
 */
class FirestoreClient(
    private val apiKey: String,
    private val settingsStore: org.taskhub.storage.SettingsStore
) {
    private val authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
    private val secureTokenUrl = "https://securetoken.googleapis.com/v1/token"

    // ── Auth state (in-memory, regenerated on app restart — fine for anonymous) ──
    @Volatile
    var bearerToken: String? = null
        private set
    @Volatile
    var tokenExpiry: Long = 0L  // epoch millis when token expires (minus safety margin)
        private set
    @Volatile
    var cachedLocalId: String? = null  // anonymous user ID — persists across sessions via settings
        private set
    // Serializa ensureAuth(): sin esto, ráfagas de llamadas paralelas (varias
    // pantallas cargando datos a la vez tras un cold start) pasan todas el
    // check "bearerToken == null" antes de que la primera termine de escribirlo,
    // disparando N altas/refrescos de token concurrentes.
    private val authMutex = Mutex()

    /** Json tolerante usado solo para parsear el body de error de Firestore, no el de dominio. */
    private val errorParsingJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
        // Intercepta CUALQUIER respuesta de error (>=400) antes de que se parsee
        // como documento — si no, un body de error de Firestore se convierte en
        // un FirestoreDocumentResponse vacío (por ignoreUnknownKeys) y el fallo
        // real (p.ej. PERMISSION_DENIED) queda enmascarado tras "missing document name".
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value >= 400) {
                    val bodyText = runCatching { response.bodyAsText() }.getOrDefault("")
                    val errorBody = runCatching {
                        errorParsingJson.decodeFromString<FirestoreErrorEnvelope>(bodyText)
                    }.getOrNull()?.error
                    val message = errorBody?.message?.takeIf { it.isNotBlank() }
                        ?: bodyText.takeIf { it.isNotBlank() }
                        ?: "Firestore respondió ${response.status.value} sin más detalles"
                    throw FirestoreException(
                        statusCode = response.status.value,
                        code = errorBody?.status,
                        message = message
                    )
                }
            }
        }
    }

    /**
     * Ensures we have a valid anonymous auth token, signing up anonymously if needed.
     * Called lazily on first request. Token is cached in memory and refreshed
     * when within 5 minutes of expiry.
     *
     * POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=API_KEY
     * Body: {"returnSecureToken":true}
     */
    suspend fun ensureAuth() = authMutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        if (bearerToken != null && now < tokenExpiry) return@withLock

        // 1) Restaurar sesión de Google si existe (UID estable del login Google).
        val googleRefresh = settingsStore.getGoogleRefreshToken()
        if (settingsStore.getGoogleUid() != null && googleRefresh != null) {
            try {
                val refreshed = refreshFirebaseToken(googleRefresh)
                bearerToken = refreshed.idToken
                cachedLocalId = refreshed.userId
                tokenExpiry = refreshed.tokenExpiry
                settingsStore.setGoogleRefreshToken(refreshed.refreshToken ?: googleRefresh)
                return@withLock
            } catch (_: Exception) {
                // Sesión de Google caducada → cae al flujo anónimo.
                settingsStore.clearGoogleAuth()
            }
        }

        // 2) Restaurar la identidad anónima persistida (mismo UID entre reinicios).
        val savedRefresh = settingsStore.getAnonymousRefreshToken()
        if (savedRefresh != null) {
            try {
                val refreshed = refreshFirebaseToken(savedRefresh)
                bearerToken = refreshed.idToken
                cachedLocalId = refreshed.userId
                tokenExpiry = refreshed.tokenExpiry
                settingsStore.saveAnonymousAuth(refreshed.refreshToken ?: savedRefresh, refreshed.userId)
                return@withLock
            } catch (_: Exception) {
                // Token caducado/revocado → alta anónima nueva.
                settingsStore.clearAnonymousAuth()
            }
        }

        // 3) Alta anónima nueva (sin email/password) y persistir el refresh token.
        val response: FirebaseAuthResponse = client.post("$authUrl?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(FirebaseAuthRequest(returnSecureToken = true))
        }.body()

        val idToken = response.idToken
        val localId = response.localId
        val expiresIn = response.expiresIn?.toLongOrNull()
        val refreshToken = response.refreshToken
        if (idToken.isNullOrBlank() || localId.isNullOrBlank() || expiresIn == null || refreshToken.isNullOrBlank()) {
            throw IllegalStateException(
                "Firebase anonymous auth devolvió una respuesta incompleta " +
                "(sin idToken/localId/expiresIn/refreshToken). Verifica la API key del proyecto."
            )
        }

        bearerToken = idToken
        cachedLocalId = localId
        // expiresIn is in seconds. Refresh 5 minutes before actual expiry.
        tokenExpiry = now + (expiresIn * 1000) - 300_000
        settingsStore.saveAnonymousAuth(refreshToken, localId)
    }

    /**
     * Renueva un idToken de Firebase Auth usando su refresh token, sin crear una
     * identidad nueva. Devuelve el MISMO UID (user_id), de modo que el usuario
     * (anónimo o de Google) conserva sus datos entre reinicios y reinstalaciones.
     *
     * Endpoint: POST https://securetoken.googleapis.com/v1/token?key=API_KEY
     * Body (form-urlencoded): grant_type=refresh_token&refresh_token=...
     */
    private suspend fun refreshFirebaseToken(refreshToken: String): RefreshedAuth {
        val response: TokenRefreshResponse = client.post("$secureTokenUrl?key=$apiKey") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            }))
        }.body()

        val idToken = response.id_token
        val userId = response.user_id
        val expiresIn = response.expires_in?.toLongOrNull()
        if (idToken.isNullOrBlank() || userId.isNullOrBlank() || expiresIn == null) {
            throw IllegalStateException("Renovación del token de Firebase falló (respuesta incompleta)")
        }

        val now = Clock.System.now().toEpochMilliseconds()
        return RefreshedAuth(
            idToken = idToken,
            userId = userId,
            tokenExpiry = now + (expiresIn * 1000) - 300_000,
            refreshToken = response.refresh_token
        )
    }

    private data class RefreshedAuth(
        val idToken: String,
        val userId: String,
        val tokenExpiry: Long,
        val refreshToken: String? = null
    )

    /**
     * Fija el estado de auth (token/uid/expiry) directamente, sin pasar por
     * [ensureAuth]. Lo usa [FirestoreRepository.signInWithGoogle] tras
     * intercambiar el idToken de Google por uno de Firebase — ese flujo no es
     * "asegurar" un token existente sino sustituirlo por uno nuevo de sesión.
     */
    fun setAuthState(idToken: String, localId: String, expiry: Long) {
        bearerToken = idToken
        cachedLocalId = localId
        tokenExpiry = expiry
    }

    /**
     * Adds Authorization header to a request builder if we already have a token.
     * Calls [ensureAuth] first so the token is always fresh.
     */
    suspend fun HttpRequestBuilder.withAuth() {
        ensureAuth()
        bearerToken?.let { header("Authorization", "Bearer $it") }
    }

    /**
     * Tries Bearer auth first; falls back to API key parameter.
     * Used for read operations where API key alone might suffice.
     */
    suspend fun HttpRequestBuilder.tryAuthOrApiKey() {
        try {
            ensureAuth()
            bearerToken?.let { header("Authorization", "Bearer $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Auth failed — fall back to API key for read-only access
            parameter("key", apiKey)
        }
    }

    /**
     * Añade `updateMask.fieldPaths` como parámetros de query repetidos (uno por
     * campo), tal y como exige la API REST de Firestore. Un único string con
     * los campos unidos por comas ("a,b,c") es inválido y produce el error
     * "Invalid property path" — Firestore espera múltiples pares
     * `updateMask.fieldPaths=a&updateMask.fieldPaths=b&updateMask.fieldPaths=c`.
     */
    fun HttpRequestBuilder.updateMaskFieldPaths(vararg fields: String) {
        fields.forEach { parameter("updateMask.fieldPaths", it) }
    }

    fun HttpRequestBuilder.updateMaskFieldPaths(fields: Collection<String>) {
        fields.forEach { parameter("updateMask.fieldPaths", it) }
    }

    /** Ver [FirestoreParsers.extractDocId] — extraído para ser testable sin I/O. */
    fun extractDocId(resourceName: String, operation: String): String =
        FirestoreParsers.extractDocId(resourceName, operation)

    /**
     * Borra la cuenta de Firebase Auth actual (Google o anónima) vía el REST
     * de Identity Toolkit. Paso final e irreversible del flujo "eliminar
     * cuenta" (ver [org.taskhub.ui.models.GoogleAuthManager.deleteAccount]):
     * debe llamarse SOLO después de borrar los datos del usuario en
     * Firestore, porque una vez borrada la cuenta el idToken deja de ser
     * válido para cualquier escritura posterior.
     *
     * Endpoint: POST https://identitytoolkit.googleapis.com/v1/accounts:delete?key=API_KEY
     * Body: {"idToken": "..."}
     */
    suspend fun deleteFirebaseAccount() {
        ensureAuth()
        val token = bearerToken
            ?: throw IllegalStateException("No hay sesión activa para eliminar la cuenta")
        client.post("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(token))
        }
    }

    companion object {
        /** Reintentos ante conflicto de concurrencia optimista (ver `addMemberPoints`/`addMemberAchievement`). */
        const val OPTIMISTIC_WRITE_MAX_RETRIES = 3
    }
}

/**
 * Recorre una colección de Firestore paginando con `pageToken` hasta
 * agotarla, en vez de una única petición sin `pageSize` — el REST de
 * Firestore no garantiza devolver la colección completa en una sola
 * respuesta, así que sin este bucle una colección que creciera por encima
 * del tamaño de página del servidor se leería truncada, en silencio, sin
 * ningún error visible (ver docs/review-panel-expertos-v3-2026-09-01.md,
 * hallazgo de Escalabilidad "sin paginación en ninguna colección").
 * `pageSize` por defecto (300) es generoso para los hogares reales de hoy —
 * en la práctica el bucle da una sola vuelta — pero deja de truncar si un
 * hogar crece. `configureAuth` recibe la misma lambda que ya usan los
 * call-sites (`tryAuthOrApiKey()`/`withAuth()`), definida en el repo llamante
 * porque son extension functions con receptor [FirestoreClient].
 */
internal suspend fun HttpClient.listAllDocuments(
    url: String,
    pageSize: Int = 300,
    configureAuth: suspend HttpRequestBuilder.() -> Unit
): List<FirestoreDocumentResponse> {
    val documents = mutableListOf<FirestoreDocumentResponse>()
    var pageToken: String? = null
    var page = 0
    do {
        val response: FirestoreListResponse = get(url) {
            configureAuth()
            parameter("pageSize", pageSize)
            pageToken?.let { parameter("pageToken", it) }
        }.body()
        documents += response.documents
        pageToken = response.nextPageToken
        page++
        // Tope de seguridad ante un backend/proxy que devolviera un
        // nextPageToken no-null indefinidamente (mismo criterio que el
        // `safety` de RecurrenceRules.nextOccurrence) — sin esto, ese
        // escenario dejaría la corrutina reintentando peticiones HTTP sin
        // fin. 200 páginas × 300 = 60.000 documentos, muy por encima de
        // cualquier hogar real.
        check(page < 200) { "listAllDocuments: demasiadas páginas para $url (posible bucle de paginación)" }
    } while (pageToken != null)
    return documents
}

/**
 * Ejecuta [block] y devuelve [default] ante cualquier fallo NO fatal, pero
 * relanza [CancellationException] para no romper la cancelación cooperativa
 * de la corrutina. Compartida por los repos de dominio de `network/` (antes
 * duplicada 4 veces, una copia idéntica por archivo).
 */
internal suspend inline fun <T> orDefault(default: T, block: () -> T): T {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        default
    }
}
