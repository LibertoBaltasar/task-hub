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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.taskhub.network.models.HouseholdResponse
import org.taskhub.network.models.MemberResponse
import org.taskhub.network.models.UserProfile
import org.taskhub.network.models.TaskHistoryResponse
import org.taskhub.network.models.TaskResponse
import org.taskhub.network.models.TaskAssignmentResponse
import org.taskhub.network.models.NotificationResponse
import org.taskhub.network.models.RewardResponse
import org.taskhub.network.models.RewardRedemption
import org.taskhub.network.models.Subtask
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SavedHousehold
import org.taskhub.storage.SettingsStore
import org.taskhub.storage.TaskCache
import org.taskhub.platform.secureRandomInt

/**
 * Talks directly to Firestore REST API — no Ktor server needed.
 *
 * Firestore REST API docs:
 *   https://firebase.google.com/docs/firestore/reference/rest
 *
 * Uses Firebase Anonymous Auth for write access.
 * The API key alone only allows reads — writes require a Bearer token.
 * Anonymous Auth requires zero user interaction (no Google Sign-In, no UI).
 */
class FirestoreRepository(
    private val projectId: String = "task-hub-62f98",
    private val apiKey: String = DEFAULT_API_KEY,
    private val taskCache: TaskCache,
    private val settingsStore: SettingsStore
) {
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
    private val authUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
    private val secureTokenUrl = "https://securetoken.googleapis.com/v1/token"

    // ── Auth state (in-memory, regenerated on app restart — fine for anonymous) ──
    @Volatile
    private var bearerToken: String? = null
    @Volatile
    private var tokenExpiry: Long = 0L  // epoch millis when token expires (minus safety margin)
    @Volatile
    private var cachedLocalId: String? = null  // anonymous user ID — persists across sessions via settings

    /** Json tolerante usado solo para parsear el body de error de Firestore, no el de dominio. */
    private val errorParsingJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
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

    // ────────────────────────────────────────────────────────
    //  Auth
    // ────────────────────────────────────────────────────────

    /**
     * Ensures we have a valid anonymous auth token, signing up anonymously if needed.
     * Called lazily on first request. Token is cached in memory and refreshed
     * when within 5 minutes of expiry.
     *
     * POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=API_KEY
     * Body: {"returnSecureToken":true}
     */
    private suspend fun ensureAuth() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (bearerToken != null && now < tokenExpiry) return

        // 1) Restaurar sesión de Google si existe (UID estable del login Google).
        val googleRefresh = settingsStore.getGoogleRefreshToken()
        if (settingsStore.getGoogleUid() != null && googleRefresh != null) {
            try {
                val refreshed = refreshFirebaseToken(googleRefresh)
                bearerToken = refreshed.idToken
                cachedLocalId = refreshed.userId
                tokenExpiry = refreshed.tokenExpiry
                settingsStore.setGoogleRefreshToken(refreshed.refreshToken ?: googleRefresh)
                return
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
                return
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
     * Devuelve el UID del usuario actual (anónimo o Google). Cae al UID persistido
     * si aún no se ha autenticado en esta sesión, para que esté disponible antes
     * de la primera llamada de red (p.ej. al crear el miembro "Yo" del Personal).
     */
    fun getLocalId(): String? =
        cachedLocalId ?: settingsStore.getGoogleUid() ?: settingsStore.getAnonymousUid()

    /**
     * Todas las identidades posibles del usuario actual, sin duplicados:
     * UID de Google (si ha iniciado sesión), UID anónimo persistido y el UID
     * activo en esta sesión. Sirve para resolver "¿este miembro soy yo?" con
     * independencia de en qué momento se creó el miembro (antes o después de
     * vincular Google), lo que evita duplicados al unirse y perfiles que no
     * se borran al salir.
     */
    fun currentUserIdentities(): List<String> =
        listOfNotNull(
            settingsStore.getGoogleUid(),
            settingsStore.getAnonymousUid(),
            cachedLocalId
        ).distinct()

    /**
     * Inicia sesión con Google: intercambia un Google idToken por un token de
     * Firebase Auth vía accounts:signInWithIdp. Devuelve el UID estable de Google
     * (localId) + email/displayName, que persisten entre reinstalaciones.
     */
    suspend fun signInWithGoogle(googleIdToken: String): GoogleSignInResult {
        val response: FirebaseAuthResponse = client.post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(
                SignInWithIdpRequest(
                    postBody = "id_token=$googleIdToken&providerId=google.com",
                    requestUri = "http://localhost",
                    returnSecureToken = true
                )
            )
        }.body()

        val idToken = response.idToken
        val localId = response.localId
        val expiresIn = response.expiresIn?.toLongOrNull()
        if (idToken.isNullOrBlank() || localId.isNullOrBlank() || expiresIn == null) {
            throw IllegalStateException(
                "Google sign-in falló: respuesta de Firebase Auth incompleta. " +
                "Verifica que el proveedor Google esté habilitado en Firebase Auth."
            )
        }

        val now = Clock.System.now().toEpochMilliseconds()
        bearerToken = idToken
        cachedLocalId = localId
        tokenExpiry = now + (expiresIn * 1000) - 300_000

        // Persistir la sesión de Google para restaurarla en próximos arranques
        // (el refresh token permite renovar el idToken sin re-login).
        settingsStore.setGoogleRefreshToken(response.refreshToken)
        // La identidad de Google sustituye a la anónima.
        settingsStore.clearAnonymousAuth()

        return GoogleSignInResult(
            uid = localId,
            email = response.email,
            displayName = response.displayName,
            photoUrl = response.photoUrl
        )
    }

    /** Resultado del login con Google. */
    data class GoogleSignInResult(
        val uid: String,
        val email: String? = null,
        val displayName: String? = null,
        /** Foto de perfil de la cuenta de Google, si Firebase Auth la expone. */
        val photoUrl: String? = null
    )

    /**
     * Persiste en Firestore la lista de IDs de hogares a los que pertenece el
     * usuario (documento users/{uid}). Permite restaurar los hogares tras una
     * reinstalación cuando el usuario vuelve a iniciar sesión con Google.
     */
    suspend fun saveUserHouseholds(uid: String, householdIds: List<String>) {
        val fields = mapOf(
            "householdIds" to FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = householdIds.map { FirestoreValue(stringValue = it) }
                )
            ),
            "updatedAt" to FirestoreValue(integerValue = Clock.System.now().toEpochMilliseconds().toString())
        )
        client.patch("$baseUrl/users/$uid") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Sube el token de notificaciones push (FCM) del dispositivo actual al
     * perfil global del usuario (users/{uid}.fcmToken), para que un backend/
     * Cloud Function pueda dirigirle un push (p.ej. "tarea asignada"). Se
     * limita a los campos fcmToken/fcmTokenUpdatedAt vía updateMask para no
     * pisar otros campos del perfil (displayName, avatar, etc.).
     */
    suspend fun saveFcmToken(uid: String, token: String) {
        val fields = mapOf(
            "fcmToken" to FirestoreValue(stringValue = token),
            "fcmTokenUpdatedAt" to FirestoreValue(integerValue = Clock.System.now().toEpochMilliseconds().toString())
        )
        client.patch("$baseUrl/users/$uid") {
            withAuth()
            parameter("updateMask.fieldPaths", "fcmToken,fcmTokenUpdatedAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Recupera de Firestore los IDs de hogares guardados para el usuario.
     * Devuelve lista vacía si no existe el documento users/{uid}.
     */
    suspend fun loadUserHouseholds(uid: String): List<String> = orDefault(emptyList()) {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/users/$uid") {
            tryAuthOrApiKey()
        }.body()
        response.fields["householdIds"]?.arrayValue?.values
            ?.mapNotNull { it.stringValue }
            ?: emptyList()
    }

    /**
     * Quick connectivity check — HEAD request to Firestore REST API.
     * Returns true if the network is reachable, false otherwise.
     */
    suspend fun isOnline(): Boolean {
        return try {
            client.get("$baseUrl/households/__ping__") {
                parameter("key", apiKey)
            }
            true
        } catch (e: FirestoreException) {
            // Firestore respondió con un status HTTP (aunque sea 404/403): hay red.
            true
        } catch (_: Exception) {
            // Fallo de transporte (sin red): no se llegó a Firestore.
            false
        }
    }

    /**
     * Adds Authorization header to a request builder if we already have a token.
     * Calls [ensureAuth] first so the token is always fresh.
     */
    private suspend fun HttpRequestBuilder.withAuth() {
        ensureAuth()
        bearerToken?.let { header("Authorization", "Bearer $it") }
    }

    // ────────────────────────────────────────────────────────
    //  Households
    // ────────────────────────────────────────────────────────

    /** Create a household (auto-generated doc ID). Requires auth (write). */
    suspend fun createHousehold(name: String, isPersonal: Boolean = false): HouseholdResponse {
        ensureAuth()
        val now = Clock.System.now().toEpochMilliseconds()
        val inviteCode = if (isPersonal) "PERSONAL" else generateInviteCode()
        val ownerId = getLocalId() ?: throw IllegalStateException("No autenticado")

        val fields = mapOf(
            "name" to FirestoreValue(stringValue = name),
            "inviteCode" to FirestoreValue(stringValue = inviteCode),
            "isPersonal" to FirestoreValue(booleanValue = isPersonal),
            "ownerId" to FirestoreValue(stringValue = ownerId),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createHousehold")

        // Publicar el mapa código → hogar para poder unirse sin listar hogares.
        if (!isPersonal) {
            try {
                createInvite(inviteCode, id)
            } catch (_: Exception) {
                // No crítico: sin invite, otros no pueden unirse por código.
            }
        }

        val household = HouseholdResponse(id, name, inviteCode, now, now, isPersonal, ownerId)
        // Cache immediately so getHousehold has it on first load
        taskCache.cacheHousehold(household)
        return household
    }

    /**
     * Obtiene (o crea) el espacio Personal del usuario actual con un ID DETERMINISTA
     * derivado de su identidad estable: `personal_{uid}`, donde `uid` es el UID de
     * Google si hay sesión iniciada, o el UID anónimo persistido en caso contrario.
     *
     * Esto hace el espacio Personal interdispositivo: con la misma cuenta de Google,
     * todos los dispositivos resuelven el MISMO documento `households/personal_{uid}`,
     * así que tareas/miembros/puntos se comparten automáticamente. En modo anónimo
     * (sin cuenta) sigue siendo por-dispositivo, como antes.
     */
    suspend fun getOrCreatePersonalHousehold(): HouseholdResponse {
        ensureAuth()
        val uid = getLocalId() ?: throw IllegalStateException("No autenticado")
        val personalId = personalHouseholdId(uid)

        // 1) Si ya existe (lo creó este u otro dispositivo con la misma cuenta),
        //    devolverlo tal cual y refrescar la caché local.
        val existing = try {
            getHousehold(personalId)
        } catch (_: Exception) {
            null
        }
        if (existing != null) return existing

        // 2) No existe → crearlo en el ID determinista.
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "name" to FirestoreValue(stringValue = "Personal"),
            "inviteCode" to FirestoreValue(stringValue = "PERSONAL"),
            "isPersonal" to FirestoreValue(booleanValue = true),
            "ownerId" to FirestoreValue(stringValue = uid),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )
        val response: FirestoreDocumentResponse = try {
            client.post("$baseUrl/households") {
                withAuth()
                parameter("documentId", personalId)
                contentType(ContentType.Application.Json)
                setBody(FirestoreDocument(fields))
            }.body()
        } catch (e: FirestoreException) {
            // Carrera entre dispositivos: el mismo usuario abrió la app en dos
            // sitios a la vez y ambos intentaron crear el mismo ID determinista.
            // El que llega segundo recibe ALREADY_EXISTS: no es un fallo real,
            // basta con leer el hogar que el otro dispositivo acaba de crear.
            if (e.code == "ALREADY_EXISTS" || e.statusCode == 409) {
                return getHousehold(personalId)
            }
            throw e
        }

        val household = HouseholdResponse(
            id = extractDocId(response.name, "getOrCreatePersonalHousehold"),
            name = "Personal",
            inviteCode = "PERSONAL",
            createdAt = now,
            updatedAt = now,
            isPersonal = true,
            ownerId = uid
        )
        taskCache.cacheHousehold(household)
        return household
    }

    /** ID determinista del espacio Personal para una identidad (UID) dada. */
    fun personalHouseholdId(uid: String): String = "personal_$uid"

    /**
     * Escribe invites/{code} → { householdId }. Las reglas de Firestore permiten
     * resolverlo por código (get) pero no listar la colección, de modo que el
     * código actúa como secreto compartido fuera de banda.
     */
    private suspend fun createInvite(code: String, householdId: String) {
        val fields = mapOf("householdId" to FirestoreValue(stringValue = householdId))
        client.post("$baseUrl/invites") {
            withAuth()
            parameter("documentId", code)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Get a household by id. Falls back to local cache on network/5xx failures.
     *
     * A 404/403 from Firestore is a DEFINITIVE signal — the household was deleted
     * or we lost access to it — so it must NOT fall back to the stale cache (that
     * would keep showing a "ghost" household forever). Callers that need to prune
     * local state should catch [FirestoreException] and check [FirestoreException.statusCode].
     */
    suspend fun getHousehold(id: String): HouseholdResponse {
        return try {
            val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$id") {
                tryAuthOrApiKey()
            }.body()

            val household = toHouseholdResponse(response, knownId = id)
            taskCache.cacheHousehold(household)
            household
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirestoreException) {
            if (e.statusCode == 404 || e.statusCode == 403) throw e
            taskCache.getCachedHousehold(id) ?: throw e
        } catch (e: Exception) {
            taskCache.getCachedHousehold(id) ?: throw e
        }
    }

    /**
     * Reconcilia los hogares guardados localmente en [store] contra Firestore
     * (fuente de verdad). Poda (borra de [store]) los que ya no existen o a los
     * que se perdió acceso (404/403 — señal inequívoca). Ante fallos de red o de
     * servidor (5xx, timeouts, sin conexión) CONSERVA la entrada: no podar nunca
     * por un fallo transitorio, solo por una confirmación explícita de Firestore.
     *
     * Devuelve la lista de hogares que sobreviven la reconciliación.
     */
    suspend fun reconcileHouseholds(store: HouseholdStore): List<SavedHousehold> {
        val saved = store.getSavedHouseholds()
        return saved.filter { h ->
            try {
                getHousehold(h.id)
                true
            } catch (e: FirestoreException) {
                if (e.statusCode == 404 || e.statusCode == 403) {
                    store.removeHousehold(h.id)
                    taskCache.clearHousehold(h.id)
                    false
                } else {
                    true // 5xx u otro error tipado de Firestore: no es inequívoco, conservar
                }
            } catch (_: Exception) {
                true // red/timeout/etc: conservar
            }
        }
    }

    /** Batch-fetch multiple households by their document IDs (en paralelo). */
    suspend fun getHouseholds(ids: List<String>): List<HouseholdResponse> {
        if (ids.isEmpty()) return emptyList()
        return coroutineScope {
            ids.map { id ->
                async {
                    try {
                        getHousehold(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null // stale ID from local store — skip
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Delete a household document. Does NOT cascade-delete subcollections (members, tasks)
     *  — those become orphaned but harmless. Requires auth (write). */
    suspend fun deleteHousehold(householdId: String) {
        client.delete("$baseUrl/households/$householdId") {
            withAuth()
        }
        taskCache.clearHousehold(householdId)
    }

    /**
     * Desvincula al usuario actual de un hogar: borra (DELETE real) los miembros
     * cuyo [currentUserId] coincide y, si no queda ningún miembro, elimina el
     * hogar completo de la base de datos.
     *
     * Devuelve true si el hogar se eliminó por completo (no quedaban miembros).
     * Si [currentUserId] es null (usuario anónimo sin auth aún), no borra miembros
     * pero sí comprueba si el hogar queda vacío.
     */
    suspend fun leaveHousehold(householdId: String, currentUserId: String?): Boolean {
        // Resolver TODAS las identidades del usuario (Google + anónimo), para que
        // el miembro se borre aunque se haya creado antes de vincular Google.
        val identities = (currentUserIdentities() + listOfNotNull(currentUserId)).distinct()

        val members = try {
            getMembers(householdId)
        } catch (_: Exception) {
            emptyList()
        }
        // Borrado real de los miembros que nos pertenecen.
        val deleted = members.filter { it.userId != null && it.userId in identities }
        deleted.forEach { member ->
            try {
                client.delete("$baseUrl/households/$householdId/members/${member.id}") {
                    withAuth()
                }
            } catch (_: Exception) {
                // No crítico: si el doc ya no existe, seguimos.
            }
        }

        val remaining = try {
            getMembers(householdId)
        } catch (_: Exception) {
            emptyList()
        }
        if (remaining.isEmpty()) {
            try {
                deleteHousehold(householdId)
            } catch (e: FirestoreException) {
                // Dos miembros abandonando casi a la vez pueden intentar borrar
                // el mismo hogar; si ya no existe (404), el objetivo -que el
                // hogar no exista- ya se cumplió, así que no es un fallo real.
                if (e.statusCode != 404) throw e
            }
            return true
        }
        return false
    }

    /** Find a household by invite code. Uses the invites/{code} map (no list). */
    suspend fun joinHousehold(inviteCode: String): HouseholdResponse {
        // 1) Resolver código → householdId vía invites/{code}.
        val inviteResponse: FirestoreDocumentResponse = client.get("$baseUrl/invites/$inviteCode") {
            tryAuthOrApiKey()
        }.body()

        val householdId = inviteResponse.fields["householdId"]?.stringValue
            ?: throw IllegalStateException("Código de invitación inválido")

        // 2) Leer el hogar por su ID.
        return getHousehold(householdId)
    }

    // ────────────────────────────────────────────────────────
    //  Household membership
    // ────────────────────────────────────────────────────────

    /**
     * Check if any of the given [userIds] (identidades del usuario) ya es
     * miembro del hogar. Acepta una lista porque un mismo usuario puede tener
     * UID anónimo y UID de Google, y el miembro pudo crearse con cualquiera.
     */
    suspend fun isMember(householdId: String, userIds: List<String>): Boolean = orDefault(false) {
        getMembers(householdId).any { it.userId != null && it.userId in userIds }
    }

    /** ¿El usuario actual (Google o anónimo) ya es miembro de este hogar? */
    suspend fun isCurrentUserMember(householdId: String): Boolean =
        isMember(householdId, currentUserIdentities())

    // ────────────────────────────────────────────────────────
    //  Members (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /**
     * List members of a household. Falls back to local cache if offline.
     *
     * Un 404/403 es una señal DEFINITIVA (hogar borrado / acceso perdido, igual
     * que en [getHousehold]) y se relanza en vez de devolver la caché stale;
     * solo fallos de transporte/servidor caen a caché.
     */
    suspend fun getMembers(householdId: String): List<MemberResponse> {
        return try {
            val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/members") {
                tryAuthOrApiKey()
            }.body()

            val members = response.documents
                .map { toMemberResponse(it, householdId) }
                .filter { it.leftAt == 0L }  // ocultar miembros que abandonaron (soft-delete)
            taskCache.cacheMembers(householdId, members)
            members
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirestoreException) {
            if (e.statusCode == 404 || e.statusCode == 403) throw e
            taskCache.getCachedMembers(householdId) ?: throw e
        } catch (e: Exception) {
            taskCache.getCachedMembers(householdId) ?: throw e
        }
    }

    /** Add a member to a household. Requires auth (write). */
    suspend fun createMember(
        householdId: String,
        displayName: String,
        role: String = "child",
        avatarUrl: String? = null,
        userId: String? = null,
        inviteCode: String? = null
    ): MemberResponse {
        // Deduplicación: si este usuario ya es miembro del hogar, devolvemos el
        // miembro existente en lugar de crear un duplicado (p. ej. al re-unirse).
        if (userId != null) {
            val existing = try {
                getMembers(householdId).firstOrNull { it.userId == userId }
            } catch (_: Exception) {
                null
            }
            if (existing != null) return existing
        }

        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "householdId" to FirestoreValue(stringValue = householdId),
            "displayName" to FirestoreValue(stringValue = displayName),
            "role" to FirestoreValue(stringValue = role),
            "totalPoints" to FirestoreValue(integerValue = "0"),
            "joinedAt" to FirestoreValue(integerValue = now.toString()),
            "currentStreak" to FirestoreValue(integerValue = "0"),
            "bestStreak" to FirestoreValue(integerValue = "0"),
            "lastStreakDate" to FirestoreValue(integerValue = "0"),
            "appreciationGiven" to FirestoreValue(integerValue = "0"),
            "appreciationWeekStart" to FirestoreValue(integerValue = "0")
        )
        if (avatarUrl != null) {
            fields["avatarUrl"] = FirestoreValue(stringValue = avatarUrl)
        } else {
            fields["avatarUrl"] = FirestoreValue(nullValue = "NULL_VALUE")
        }
        if (userId != null) {
            fields["userId"] = FirestoreValue(stringValue = userId)
        } else {
            fields["userId"] = FirestoreValue(nullValue = "NULL_VALUE")
        }
        // Solo se incluye al auto-unirse: las reglas validan este código
        // contra el inviteCode del hogar para autorizar la creación.
        if (inviteCode != null) {
            fields["inviteCode"] = FirestoreValue(stringValue = inviteCode)
        }

        val response: FirestoreDocumentResponse =
            if (userId != null) {
                // Miembro vinculado a una cuenta → documento keyed por su UID,
                // para que las reglas puedan verificar membresía con exists().
                client.post("$baseUrl/households/$householdId/members") {
                    withAuth()
                    parameter("documentId", userId)
                    contentType(ContentType.Application.Json)
                    setBody(FirestoreDocument(fields))
                }.body()
            } else {
                // Perfil "hijo/a" sin cuenta → ID automático.
                client.post("$baseUrl/households/$householdId/members") {
                    withAuth()
                    contentType(ContentType.Application.Json)
                    setBody(FirestoreDocument(fields))
                }.body()
            }

        val id = extractDocId(response.name, "createMember")

        // Reclamar el perfil global del usuario (base del "perfilado creciente").
        if (userId != null) {
            try {
                upsertUserProfile(userId = userId, displayName = displayName, avatarUrl = avatarUrl)
            } catch (_: Exception) {
                // No crítico: el perfil se puede reclamar más tarde.
            }
        }

        return MemberResponse(id, householdId, displayName, avatarUrl, role, 0, now,
            userId = userId,
            currentStreak = 0, bestStreak = 0, lastStreakDate = 0)
    }

    /**
     * Resuelve el ID del miembro que representa al usuario actual en un hogar.
     *
     * Orden de preferencia:
     *   1. Miembro cuyo [userId] coincide con el usuario autenticado (localId).
     *   2. El primer miembro existente (espacios Personales legados sin userId,
     *      u hogares donde el usuario actual aún no tiene userId asignado).
     *   3. Si no hay ningún miembro, crea uno "Yo" (admin) vinculado al usuario.
     *
     * Garantiza que completar tareas nunca falle con "No se ha identificado al
     * miembro actual", sin importar cómo se haya navegado hasta la tarea.
     */
    suspend fun resolveCurrentMember(householdId: String): String {
        // Asegura autenticación para que getLocalId() devuelva el UID real
        // (persistido) y no null en el primer arranque.
        ensureAuth()
        val localId = getLocalId()
        val members = try {
            getMembers(householdId)
        } catch (_: Exception) {
            emptyList()
        }

        // 1. Miembro vinculado a cualquiera de las identidades del usuario
        val identities = currentUserIdentities()
        members.firstOrNull { it.userId != null && it.userId in identities }?.let { return it.id }

        // 2. Fallback: primer miembro existente
        if (members.isNotEmpty()) return members.first().id

        // 3. Sin miembros: crear uno "Yo" vinculado al usuario actual.
        // Si createMember falla (p. ej. Firestore devuelve una respuesta sin
        // 'name', o la creación es rechazada por las reglas), no se propaga la
        // excepción: se usa el localId como fallback para que abrir un hogar
        // sin miembros nunca crashee la UI.
        return try {
            createMember(
                householdId = householdId,
                displayName = "Yo",
                role = "admin",
                userId = localId
            ).id
        } catch (_: Exception) {
            localId ?: ""
        }
    }

    /**
     * Asegura que el espacio Personal tenga un miembro "Yo".
     * Sin él, completar tareas falla con "No se ha identificado al miembro actual".
     * Idempotente: si ya hay miembros, devuelve el primero sin crear nada.
     * Cubre también la migración de espacios Personales creados antes de este fix.
     * Delega en [resolveCurrentMember], que además vincula al usuario autenticado.
     */
    suspend fun ensurePersonalMember(householdId: String): String =
        resolveCurrentMember(householdId)

    /** Remove (leave) a member — soft-delete by setting leftAt. Requires auth (write). */
    suspend fun deleteMember(householdId: String, memberId: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf(
            "leftAt" to FirestoreValue(integerValue = now.toString())
        )

        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "leftAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        return true
    }

    /**
     * Edita el rol de un miembro ("admin" | "child"). Permite reasignar el rol
     * sin recrear el miembro (parte del "perfilado" estable por usuario).
     */
    suspend fun updateMemberRole(householdId: String, memberId: String, role: String) {
        val fields = mapOf(
            "role" to FirestoreValue(stringValue = role)
        )
        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "role")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    // ────────────────────────────────────────────────────────
    //  User profile (perfilado global, colección users/{userId})
    // ────────────────────────────────────────────────────────

    /** Lee el perfil global de un usuario, o null si aún no existe. */
    suspend fun getUserProfile(userId: String): UserProfile? = orDefault(null) {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/users/$userId") {
            tryAuthOrApiKey()
        }.body()
        val f = response.fields
        UserProfile(
            id = userId,
            displayName = f["displayName"]?.stringValue ?: "",
            avatarUrl = f["avatarUrl"]?.stringValue,
            avatarEmoji = f["avatarEmoji"]?.stringValue ?: "",
            bio = f["bio"]?.stringValue ?: "",
            status = f["status"]?.stringValue ?: "",
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
            updatedAt = f["updatedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    /**
     * Crea o actualiza el perfil global de un usuario (upsert vía PATCH).
     * Base del "perfilado creciente": añadir foto/bio/etc. luego = añadir campos
     * aquí y en [UserProfile], sin tocar la membresía por hogar.
     */
    suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        avatarUrl: String? = null,
        avatarEmoji: String = "",
        bio: String = "",
        status: String = ""
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mutableMapOf<String, FirestoreValue>(
            "displayName" to FirestoreValue(stringValue = displayName),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )
        if (avatarUrl != null) {
            fields["avatarUrl"] = FirestoreValue(stringValue = avatarUrl)
        } else {
            fields["avatarUrl"] = FirestoreValue(nullValue = "NULL_VALUE")
        }
        fields["avatarEmoji"] = FirestoreValue(stringValue = avatarEmoji)
        fields["bio"] = FirestoreValue(stringValue = bio)
        fields["status"] = FirestoreValue(stringValue = status)

        client.patch("$baseUrl/users/$userId") {
            withAuth()
            parameter("updateMask.fieldPaths", fields.keys.joinToString(","))
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Update member streak fields. Requires auth (write). */
    suspend fun updateMemberStreak(
        householdId: String,
        memberId: String,
        currentStreak: Int,
        bestStreak: Int,
        lastStreakDate: Long
    ) {
        val fields = mapOf(
            "currentStreak" to FirestoreValue(integerValue = currentStreak.toString()),
            "bestStreak" to FirestoreValue(integerValue = bestStreak.toString()),
            "lastStreakDate" to FirestoreValue(integerValue = lastStreakDate.toString())
        )
        client.patch("$baseUrl/households/$householdId/members/$memberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "currentStreak,bestStreak,lastStreakDate")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Update member total points (add delta). Requires auth (write).
     *
     * Usa concurrencia optimista (`currentDocument.updateTime`) en vez de un
     * simple leer-y-escribir: si otro dispositivo modifica el mismo documento
     * entre la lectura y la escritura, Firestore rechaza el PATCH
     * (FAILED_PRECONDITION/ABORTED) y reintentamos con el valor fresco, en vez
     * de sobrescribir a ciegas y perder el incremento concurrente.
     */
    suspend fun addMemberPoints(
        householdId: String,
        memberId: String,
        delta: Int
    ) {
        if (delta == 0) return
        val docUrl = "$baseUrl/households/$householdId/members/$memberId"
        repeat(OPTIMISTIC_WRITE_MAX_RETRIES) { attempt ->
            val current: FirestoreDocumentResponse = try {
                client.get(docUrl) { withAuth() }.body()
            } catch (e: FirestoreException) {
                if (e.statusCode == 404) return // miembro inexistente: no-op, como antes
                throw e
            }
            val newTotal = (current.fields["totalPoints"]?.integerValue?.toIntOrNull() ?: 0) + delta
            try {
                client.patch(docUrl) {
                    withAuth()
                    parameter("updateMask.fieldPaths", "totalPoints")
                    current.updateTime?.let { parameter("currentDocument.updateTime", it) }
                    contentType(ContentType.Application.Json)
                    setBody(FirestoreDocument(mapOf("totalPoints" to FirestoreValue(integerValue = newTotal.toString()))))
                }
                return
            } catch (e: FirestoreException) {
                val isConflict = e.code == "FAILED_PRECONDITION" || e.code == "ABORTED"
                if (!isConflict || attempt == OPTIMISTIC_WRITE_MAX_RETRIES - 1) throw e
                // Otro escritor ganó la carrera: reintentar con el valor fresco.
            }
        }
    }

    // ────────────────────────────────────────────────────────
    //  Appreciation ("agradecer" — acuñación con tope semanal) & donations
    // ────────────────────────────────────────────────────────

    sealed class AppreciateResult {
        data class Ok(val remaining: Int, val receptorNewTotal: Int) : AppreciateResult()
        data class Error(val reason: AppreciateErrorReason) : AppreciateResult()
    }

    enum class AppreciateErrorReason { SELF, INVALID_AMOUNT, LIMIT_EXCEEDED, MEMBER_NOT_FOUND }

    private fun PointsRules.AppreciateError.toRepoReason(): AppreciateErrorReason = when (this) {
        PointsRules.AppreciateError.SELF -> AppreciateErrorReason.SELF
        PointsRules.AppreciateError.INVALID_AMOUNT -> AppreciateErrorReason.INVALID_AMOUNT
        PointsRules.AppreciateError.LIMIT_EXCEEDED -> AppreciateErrorReason.LIMIT_EXCEEDED
    }

    sealed class DonateResult {
        data class Ok(val donorNewTotal: Int, val receptorNewTotal: Int) : DonateResult()
        data class Error(val reason: DonateErrorReason) : DonateResult()
    }

    enum class DonateErrorReason { SELF, INVALID_AMOUNT, INSUFFICIENT_BALANCE, MEMBER_NOT_FOUND }

    private fun PointsRules.DonateError.toRepoReason(): DonateErrorReason = when (this) {
        PointsRules.DonateError.SELF -> DonateErrorReason.SELF
        PointsRules.DonateError.INVALID_AMOUNT -> DonateErrorReason.INVALID_AMOUNT
        PointsRules.DonateError.INSUFFICIENT_BALANCE -> DonateErrorReason.INSUFFICIENT_BALANCE
    }

    private fun currentAppreciationBudget(member: MemberResponse, now: Long): PointsRules.AppreciationBudget =
        PointsRules.currentAppreciationBudget(member.appreciationGiven, member.appreciationWeekStart, now)

    /**
     * Puntos que [member] aún puede DAR agradeciendo esta semana (0..50), sin mutar nada.
     * Útil para que la UI muestre el presupuesto restante antes de abrir el diálogo.
     */
    fun appreciationRemaining(member: MemberResponse, now: Long = Clock.System.now().toEpochMilliseconds()): Int =
        currentAppreciationBudget(member, now).remaining

    /**
     * "Agradecer": acuña [amount] puntos para [toMemberId] sin restárselos a [fromMemberId].
     * Cada miembro puede DAR como máximo 50 puntos por semana (lunes-domingo); al superar el
     * tope semanal, o al agradecerse a sí mismo, devuelve un [AppreciateResult.Error] tipado
     * en vez de lanzar una excepción.
     */
    suspend fun appreciateMember(
        householdId: String,
        fromMemberId: String,
        toMemberId: String,
        amount: Int
    ): AppreciateResult {
        PointsRules.validateAppreciateBasic(fromMemberId, toMemberId, amount)?.let {
            return AppreciateResult.Error(it.toRepoReason())
        }

        val members = getMembers(householdId)
        val fromMember = members.find { it.id == fromMemberId }
            ?: return AppreciateResult.Error(AppreciateErrorReason.MEMBER_NOT_FOUND)
        val toMember = members.find { it.id == toMemberId }
            ?: return AppreciateResult.Error(AppreciateErrorReason.MEMBER_NOT_FOUND)

        val now = Clock.System.now().toEpochMilliseconds()
        val budget = currentAppreciationBudget(fromMember, now)
        PointsRules.validateAppreciateLimit(amount, budget)?.let {
            return AppreciateResult.Error(it.toRepoReason())
        }

        // Consumir presupuesto ANTES de acuñar puntos: si la app se cierra o la
        // red falla entre las dos escrituras, es mejor que el receptor se quede
        // sin acreditar (recuperable reintentando) a que el emisor pueda superar
        // el tope semanal reintentando una acuñación que sí llegó a completarse.
        val newGiven = budget.given + amount
        val fields = mapOf(
            "appreciationGiven" to FirestoreValue(integerValue = newGiven.toString()),
            "appreciationWeekStart" to FirestoreValue(integerValue = budget.weekStart.toString())
        )
        client.patch("$baseUrl/households/$householdId/members/$fromMemberId") {
            withAuth()
            parameter("updateMask.fieldPaths", "appreciationGiven,appreciationWeekStart")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        // Acuñar: el receptor gana puntos sin que se le resten al que agradece.
        addMemberPoints(householdId, toMemberId, amount)

        return AppreciateResult.Ok(
            remaining = PointsRules.WEEKLY_APPRECIATION_BUDGET - newGiven,
            receptorNewTotal = toMember.totalPoints + amount
        )
    }

    /**
     * "Donar": transfiere [amount] puntos reales del saldo de [fromMemberId] a [toMemberId].
     * Sin tope semanal (no es acuñación), pero no permite donarse a sí mismo ni donar más
     * del saldo actual del donante.
     */
    suspend fun donatePoints(
        householdId: String,
        fromMemberId: String,
        toMemberId: String,
        amount: Int
    ): DonateResult {
        PointsRules.validateDonateBasic(fromMemberId, toMemberId, amount)?.let {
            return DonateResult.Error(it.toRepoReason())
        }

        val members = getMembers(householdId)
        val fromMember = members.find { it.id == fromMemberId }
            ?: return DonateResult.Error(DonateErrorReason.MEMBER_NOT_FOUND)
        val toMember = members.find { it.id == toMemberId }
            ?: return DonateResult.Error(DonateErrorReason.MEMBER_NOT_FOUND)

        PointsRules.validateDonateBalance(amount, fromMember.totalPoints)?.let {
            return DonateResult.Error(it.toRepoReason())
        }

        // Restar primero al donante: si la segunda escritura falla a mitad de
        // camino, el peor caso es que los puntos "desaparezcan" (recuperable
        // reintentando la donación), nunca que se dupliquen de la nada.
        addMemberPoints(householdId, fromMemberId, -amount)
        addMemberPoints(householdId, toMemberId, amount)

        return DonateResult.Ok(
            donorNewTotal = fromMember.totalPoints - amount,
            receptorNewTotal = toMember.totalPoints + amount
        )
    }

    /** Get member's unlocked achievement IDs. */
    suspend fun getMemberAchievements(householdId: String, memberId: String): Set<String> = orDefault(emptySet()) {
        val response: FirestoreDocumentResponse = client.get(
            "$baseUrl/households/$householdId/members/$memberId/achievements/_meta"
        ) {
            tryAuthOrApiKey()
        }.body()
        response.fields["unlocked"]?.arrayValue?.values
            ?.mapNotNull { it.stringValue }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Add an achievement ID to member's unlocked achievements.
     *
     * Lee-modifica-escribe con concurrencia optimista (igual que [addMemberPoints]):
     * si dos logros se desbloquean casi a la vez (misma tarea que dispara dos, o
     * dos dispositivos), el PATCH que llega segundo ve el precondition fallar y
     * reintenta con el array ya actualizado, en vez de sobrescribirlo y perder
     * el logro que ganó la carrera.
     */
    suspend fun addMemberAchievement(householdId: String, memberId: String, achievementId: String) {
        val docUrl = "$baseUrl/households/$householdId/members/$memberId/achievements/_meta"
        val now = Clock.System.now().toEpochMilliseconds()
        repeat(OPTIMISTIC_WRITE_MAX_RETRIES) { attempt ->
            val current: FirestoreDocumentResponse? = try {
                client.get(docUrl) { withAuth() }.body()
            } catch (e: FirestoreException) {
                if (e.statusCode == 404) null else throw e
            }
            val existing = current?.fields?.get("unlocked")?.arrayValue?.values
                ?.mapNotNull { it.stringValue }?.toSet() ?: emptySet()
            if (achievementId in existing) return // ya desbloqueado
            val allUnlocked = existing + achievementId
            val fields = mapOf(
                "unlocked" to FirestoreValue(
                    arrayValue = FirestoreArrayValue(values = allUnlocked.map { FirestoreValue(stringValue = it) })
                ),
                "updatedAt" to FirestoreValue(integerValue = now.toString())
            )
            try {
                client.patch(docUrl) {
                    withAuth()
                    parameter("updateMask.fieldPaths", "unlocked,updatedAt")
                    current?.updateTime?.let { parameter("currentDocument.updateTime", it) }
                    contentType(ContentType.Application.Json)
                    setBody(FirestoreDocument(fields))
                }
                return
            } catch (e: FirestoreException) {
                val isConflict = e.code == "FAILED_PRECONDITION" || e.code == "ABORTED"
                if (!isConflict || attempt == OPTIMISTIC_WRITE_MAX_RETRIES - 1) throw e
            }
        }
    }

    // ────────────────────────────────────────────────────────
    //  Tasks (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Create a task. Returns the created task. */
    suspend fun createTask(
        householdId: String,
        createdBy: String,
        title: String,
        description: String,
        points: Int,
        frequency: String,
        recurrenceDays: List<Int>,
        recurrenceDay: Int? = null,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        dueDate: Long = 0,
        assignmentRotation: List<org.taskhub.network.models.AssignmentSlot> = emptyList()
    ): TaskResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "householdId" to FirestoreValue(stringValue = householdId),
            "createdBy" to FirestoreValue(stringValue = createdBy),
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "points" to FirestoreValue(integerValue = points.toString()),
            "frequency" to FirestoreValue(stringValue = frequency),
            "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        // Tags as array
        if (tags.isNotEmpty()) {
            fields["tags"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = tags.map { FirestoreValue(stringValue = it) }
                )
            )
        } else {
            fields["tags"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(values = emptyList())
            )
        }

        // Recurrence days as array
        if (recurrenceDays.isNotEmpty()) {
            fields["recurrenceDays"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = recurrenceDays.map { FirestoreValue(integerValue = it.toString()) }
                )
            )
        }

        // Recurrence day of month (solo "monthly")
        if (recurrenceDay != null) {
            fields["recurrenceDay"] = FirestoreValue(integerValue = recurrenceDay.toString())
        }

        // Penalty configuration
        if (penaltyMode != null) {
            fields["penaltyMode"] = FirestoreValue(stringValue = penaltyMode)
            fields["penaltyValue"] = FirestoreValue(integerValue = penaltyValue.toString())
            fields["penaltyInterval"] = FirestoreValue(stringValue = penaltyInterval)
            fields["penaltyMax"] = FirestoreValue(integerValue = penaltyMax.toString())
        }

        // Assignment rotation as array of maps
        if (assignmentRotation.isNotEmpty()) {
            fields["assignmentRotation"] = FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = assignmentRotation.map { slot ->
                        FirestoreValue(
                            mapValue = FirestoreMapValue(
                                fields = mapOf(
                                    "dayOfWeek" to FirestoreValue(integerValue = slot.dayOfWeek.toString()),
                                    "memberId" to FirestoreValue(stringValue = slot.memberId)
                                )
                            )
                        )
                    }
                )
            )
        }

        // Subtasks as array of maps
        fields["subtasks"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = subtasks.map { st ->
                    FirestoreValue(
                        mapValue = FirestoreMapValue(
                            fields = mapOf(
                                "id" to FirestoreValue(stringValue = st.id),
                                "text" to FirestoreValue(stringValue = st.text),
                                "completed" to FirestoreValue(booleanValue = st.completed)
                            )
                        )
                    )
                }
            )
        )

        val response: FirestoreDocumentResponse = client.post("$baseUrl/households/$householdId/tasks") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createTask")
        return TaskResponse(
            id = id, householdId = householdId, createdBy = createdBy,
            title = title, description = description, points = points,
            frequency = frequency, recurrenceDays = recurrenceDays,
            recurrenceDay = recurrenceDay, tags = tags,
            subtasks = subtasks,
            penaltyMode = penaltyMode, penaltyValue = penaltyValue,
            penaltyInterval = penaltyInterval, penaltyMax = penaltyMax,
            dueDate = dueDate, lastCompletedDate = null,
            assignmentRotation = assignmentRotation,
            createdAt = now, updatedAt = now
        )
    }

    /**
     * List all tasks for a household. Falls back to local cache if offline.
     *
     * Un 404/403 es una señal DEFINITIVA (hogar borrado / acceso perdido, igual
     * que en [getHousehold]) y se relanza en vez de devolver la caché stale;
     * solo fallos de transporte/servidor caen a caché.
     */
    suspend fun getTasks(householdId: String): List<TaskResponse> {
        return try {
            val response: FirestoreListResponse = client.get("$baseUrl/households/$householdId/tasks") {
                tryAuthOrApiKey()
            }.body()

            val tasks = response.documents.map { toTaskResponse(it, householdId) }
            taskCache.cacheTasks(householdId, tasks)
            tasks
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirestoreException) {
            if (e.statusCode == 404 || e.statusCode == 403) throw e
            taskCache.getCachedTasks(householdId) ?: throw e
        } catch (e: Exception) {
            taskCache.getCachedTasks(householdId) ?: throw e
        }
    }

    /** Get a single task by id. Used where only one task is needed (avoids an N+1 full-list fetch). */
    suspend fun getTask(householdId: String, taskId: String): TaskResponse {
        val response: FirestoreDocumentResponse = client.get("$baseUrl/households/$householdId/tasks/$taskId") {
            tryAuthOrApiKey()
        }.body()
        return toTaskResponse(response, householdId)
    }

    /**
     * Mark a task as completed today. Sets lastCompletedDate, awards points, and records history.
     * Devuelve el `completedAt` (epoch millis) usado, para que el caller pueda
     * localizar después el registro de `taskHistory` creado (p.ej. al deshacer).
     *
     * NO es atómica de extremo a extremo (3 escrituras HTTP secuenciales): un
     * fallo de red a mitad de secuencia deja estado parcial, mitigado por la
     * guarda de reentrancia de `TaskScreenModel` pero no eliminado. Evaluado y
     * descartado usar el endpoint `:commit` con `fieldTransforms` para
     * hacerlo transaccional — ver `docs/atomicidad-commit-pendiente.md` para
     * el motivo (no se puede verificar el payload contra la API real en este
     * entorno) y los pasos para hacerlo con seguridad en el futuro.
     */
    suspend fun completeTask(
        householdId: String,
        taskId: String,
        memberId: String,
        taskPoints: Int
    ): Long {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Update lastCompletedDate + completedBy on the task.
        //    completedBy registra QUIÉN marcó hecho (quien recibe los puntos),
        //    al margen de quién esté asignado.
        val fields = mapOf(
            "lastCompletedDate" to FirestoreValue(integerValue = now.toString()),
            "completedBy" to FirestoreValue(stringValue = memberId)
        )

        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", "lastCompletedDate,completedBy")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        // 2. Add points to the member
        addMemberPoints(householdId, memberId, taskPoints)

        // 3. Save task history record
        saveTaskHistory(
            householdId = householdId,
            taskId = taskId,
            memberId = memberId,
            points = taskPoints,
            completedAt = now,
            onTime = true
        )

        return now
    }

    /**
     * Revert a task completion — used by the undo feature.
     * Restores the previous lastCompletedDate/completedBy on the task document.
     * No revierte puntos/racha/historial por sí sola — eso lo hace el caller
     * (ver [TaskScreenModel.undoCompleteTask]: `addMemberPoints`, `updateMemberStreak`
     * y [deleteTaskHistoryRecord]).
     */
    suspend fun revertTaskCompletion(
        householdId: String,
        taskId: String,
        previousLastCompletedDate: Long?,
        previousCompletedBy: String? = null
    ) {
        val lcdValue = if (previousLastCompletedDate != null) {
            FirestoreValue(integerValue = previousLastCompletedDate.toString())
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }
        val cbValue = if (previousCompletedBy != null) {
            FirestoreValue(stringValue = previousCompletedBy)
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }
        val fields = mapOf(
            "lastCompletedDate" to lcdValue,
            "completedBy" to cbValue
        )
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", "lastCompletedDate,completedBy")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Save a task completion record to Firestore taskHistory subcollection. */
    suspend fun saveTaskHistory(
        householdId: String,
        taskId: String,
        memberId: String,
        points: Int,
        completedAt: Long,
        onTime: Boolean
    ) {
        val fields = mapOf(
            "taskId" to FirestoreValue(stringValue = taskId),
            "memberId" to FirestoreValue(stringValue = memberId),
            "points" to FirestoreValue(integerValue = points.toString()),
            "completedAt" to FirestoreValue(integerValue = completedAt.toString()),
            "onTime" to FirestoreValue(booleanValue = onTime)
        )

        client.post("$baseUrl/households/$householdId/taskHistory") {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /** Get all task history records for a household. */
    suspend fun getTaskHistory(householdId: String): List<TaskHistoryResponse> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/taskHistory"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            TaskHistoryResponse(
                id = extractDocId(doc.name, "getTaskHistory"),
                taskId = f["taskId"]?.stringValue ?: "",
                memberId = f["memberId"]?.stringValue ?: "",
                points = f["points"]?.integerValue?.toIntOrNull() ?: 0,
                completedAt = f["completedAt"]?.integerValue?.toLongOrNull() ?: 0L,
                onTime = f["onTime"]?.booleanValue ?: true
            )
        }
    }

    /**
     * Reasigna quién ha hecho una tarea ya completada (corrección de errores).
     *
     * Transfiere los puntos: resta los puntos al miembro anterior (completedBy)
     * y se los suma al nuevo, manteniendo coherentes totalPoints, completedBy
     * y el registro de historial correspondiente. Si la tarea no estaba
     * completada (completedBy null), solo fija el nuevo miembro sin transferir.
     *
     * NO es atómica de extremo a extremo (hasta 4 escrituras HTTP
     * secuenciales): ver la nota de atomicidad en [completeTask] y
     * `docs/atomicidad-commit-pendiente.md`.
     */
    suspend fun reassignTaskCompletion(
        householdId: String,
        taskId: String,
        taskPoints: Int,
        newMemberId: String
    ) {
        val task = try {
            getTask(householdId, taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val oldMemberId = task.completedBy
        val completedAt = task.lastCompletedDate ?: Clock.System.now().toEpochMilliseconds()

        // 1. Transferir puntos SOLO si había un completer previo registrado.
        //    (quien marca hecho recibe los puntos; al corregir se mueven de la
        //    persona anterior a la nueva). Si completedBy era null (tarea legacy
        //    completada antes de registrar quién), fijamos el nuevo sin tocar
        //    puntos para no duplicarlos.
        if (oldMemberId != null && oldMemberId != newMemberId) {
            addMemberPoints(householdId, oldMemberId, -taskPoints)
            addMemberPoints(householdId, newMemberId, taskPoints)
        }

        // 2. Actualizar completedBy en la tarea.
        val fields = mapOf("completedBy" to FirestoreValue(stringValue = newMemberId))
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", "completedBy")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        // 3. Reasignar el registro de historial de esa compleción para que las
        //    estadísticas (StatsScreen) sigan coherentes.
        if (oldMemberId != null && oldMemberId != newMemberId) {
            updateTaskHistoryMember(householdId, taskId, completedAt, newMemberId)
        }
    }

    /**
     * Localiza el registro de `taskHistory` de una compleción concreta
     * (identificada por taskId + completedAt). null si no existe.
     */
    private suspend fun findTaskHistoryRecord(
        householdId: String,
        taskId: String,
        completedAt: Long
    ): TaskHistoryResponse? {
        val history = try {
            getTaskHistory(householdId)
        } catch (_: Exception) {
            emptyList()
        }
        return history.firstOrNull { it.taskId == taskId && it.completedAt == completedAt }
    }

    /**
     * Actualiza el memberId del registro de historial de una compleción concreta
     * (identificada por taskId + completedAt). Se usa al corregir quién hizo una
     * tarea. No-op si no existe el registro.
     */
    private suspend fun updateTaskHistoryMember(
        householdId: String,
        taskId: String,
        completedAt: Long,
        newMemberId: String
    ) {
        val record = findTaskHistoryRecord(householdId, taskId, completedAt) ?: return
        val fields = mapOf("memberId" to FirestoreValue(stringValue = newMemberId))
        client.patch("$baseUrl/households/$householdId/taskHistory/${record.id}") {
            withAuth()
            parameter("updateMask.fieldPaths", "memberId")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Borra el registro de `taskHistory` de una compleción concreta (identificada
     * por taskId + completedAt). Se usa al deshacer una compleción (undo): sin
     * esto, el registro queda huérfano con puntos que ya no corresponden al total
     * real del miembro (que sí se revierte), desincronizando las estadísticas
     * (StatsScreen) que agregan desde `taskHistory`. No-op si no existe el registro.
     */
    suspend fun deleteTaskHistoryRecord(householdId: String, taskId: String, completedAt: Long) {
        val record = findTaskHistoryRecord(householdId, taskId, completedAt) ?: return
        try {
            client.delete("$baseUrl/households/$householdId/taskHistory/${record.id}") {
                withAuth()
            }
        } catch (_: Exception) {
            // No crítico: si ya no existe (o falla), el undo de puntos/racha sigue en pie.
        }
    }

    /** Assign a task to one or more members with a due date. */
    suspend fun assignTask(
        householdId: String,
        taskId: String,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long,
        taskTitle: String = ""
    ): List<TaskAssignmentResponse> {
        val now = Clock.System.now().toEpochMilliseconds()
        val results = mutableListOf<TaskAssignmentResponse>()

        for (memberId in memberIds) {
            val fields = mutableMapOf<String, FirestoreValue>(
                "taskId" to FirestoreValue(stringValue = taskId),
                "memberId" to FirestoreValue(stringValue = memberId),
                "mandatory" to FirestoreValue(booleanValue = mandatory),
                "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
                "status" to FirestoreValue(stringValue = "assigned"),
                "assignedAt" to FirestoreValue(integerValue = now.toString())
            )

            val response: FirestoreDocumentResponse = client.post(
                "$baseUrl/households/$householdId/tasks/$taskId/assignments"
            ) {
                withAuth()
                contentType(ContentType.Application.Json)
                setBody(FirestoreDocument(fields))
            }.body()

            val id = extractDocId(response.name, "assignTask")
            results.add(TaskAssignmentResponse(
                id = id, taskId = taskId, memberId = memberId,
                mandatory = mandatory, dueDate = dueDate, status = "assigned",
                assignedAt = now
            ))

            // Create notification for the assigned member
            createNotification(
                householdId = householdId,
                memberId = memberId,
                taskId = taskId,
                title = "\uD83D\uDCCB Tarea asignada",
                message = if (taskTitle.isNotEmpty()) "Se te ha asignado: $taskTitle" else "Se te ha asignado una nueva tarea"
            )
        }

        return results
    }

    /** Get all assignments for a specific task. */
    suspend fun getAssignments(householdId: String, taskId: String): List<TaskAssignmentResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { toTaskAssignmentResponse(it, taskId) }
    }

    /** Borra todas las asignaciones de una tarea (para reasignar al editar). */
    suspend fun deleteAssignments(householdId: String, taskId: String) {
        val assignments = try {
            getAssignments(householdId, taskId)
        } catch (_: Exception) {
            emptyList()
        }
        assignments.forEach { assignment ->
            try {
                client.delete("$baseUrl/households/$householdId/tasks/$taskId/assignments/${assignment.id}") {
                    withAuth()
                }
            } catch (_: Exception) {
                // No crítico: si ya no existe, seguimos.
            }
        }
    }

    /** Get all assignments across all tasks for a household (peticiones en paralelo). */
    suspend fun getAllAssignments(householdId: String): List<TaskAssignmentResponse> {
        val tasks = getTasks(householdId)
        return coroutineScope {
            tasks.map { task ->
                async {
                    try {
                        getAssignments(householdId, task.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList() // Task has no assignments yet — skip
                    }
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Complete a task assignment. Calculates penalty if overdue, handles recurrence.
     * Returns the updated assignment.
     */
    suspend fun completeAssignment(
        householdId: String,
        taskId: String,
        task: TaskResponse,
        assignmentId: String,
        assignment: TaskAssignmentResponse
    ): TaskAssignmentResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        // dueDate == 0 significa "sin fecha límite" (ver TaskResponse.dueDate):
        // sin este caso especial, toda tarea sin deadline se marcaba como
        // fuera de plazo (now <= 0 es siempre false) y sufría penalización.
        val onTime = assignment.dueDate == 0L || now <= assignment.dueDate

        // Calculate points (with penalty if overdue)
        val pointsAwarded = if (onTime) {
            task.points
        } else {
            val penalty = calculatePenalty(task, assignment.dueDate, now)
            maxOf(task.points - penalty, 0)
        }

        // Update assignment
        val fields = mapOf<String, FirestoreValue>(
            "status" to FirestoreValue(stringValue = "completed"),
            "completedAt" to FirestoreValue(integerValue = now.toString()),
            "pointsAwarded" to FirestoreValue(integerValue = pointsAwarded.toString()),
            "onTime" to FirestoreValue(booleanValue = onTime)
        )

        client.patch(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments/$assignmentId"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "status,completedAt,pointsAwarded,onTime")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }

        // Handle recurrence: create next assignment for recurring tasks.
        // No exigir recurrenceDays.isNotEmpty(): esa lista solo se rellena para
        // "weekly" — "daily" y "monthly" no la usan y con esa guarda nunca
        // generaban la siguiente asignación. calculateNextDueDate ya sabe
        // calcular (o devolver null) según la frecuencia.
        if (task.frequency != "once") {
            val nextDueDate = calculateNextDueDate(task, now)
            if (nextDueDate != null) {
                // Create next assignment for the same members
                assignTask(
                    householdId = householdId,
                    taskId = taskId,
                    memberIds = listOf(assignment.memberId),
                    mandatory = assignment.mandatory,
                    dueDate = nextDueDate
                )
            }
        }

        return assignment.copy(
            status = "completed",
            completedAt = now,
            pointsAwarded = pointsAwarded,
            onTime = onTime
        )
    }

    /**
     * Vincula/desvincula el evento de Google Calendar de una asignación.
     * `googleEventId = null` limpia el campo (p. ej. tras borrar el evento).
     */
    suspend fun updateAssignmentGoogleEventId(
        householdId: String,
        taskId: String,
        assignmentId: String,
        googleEventId: String?
    ) {
        val fields = mapOf(
            "googleEventId" to if (googleEventId != null) {
                FirestoreValue(stringValue = googleEventId)
            } else {
                FirestoreValue(nullValue = "NULL_VALUE")
            }
        )
        client.patch(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments/$assignmentId"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "googleEventId")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    // ────────────────────────────────────────────────────────
    //  Penalty & Recurrence Logic
    // ────────────────────────────────────────────────────────

    /**
     * Calculate penalty points for an overdue task.
     *
     * - fixed mode: subtracts `penaltyValue` per interval
     * - percentage mode: subtracts `penaltyValue`% of task.points per interval
     * - Capped at `penaltyMax` (which should not exceed task.points)
     */
    private fun calculatePenalty(task: TaskResponse, dueDate: Long, now: Long): Int {
        val mode = task.penaltyMode ?: return 0
        if (now <= dueDate) return 0

        val overdueMs = now - dueDate
        val intervalMs = when (task.penaltyInterval) {
            "week" -> 7L * 24 * 60 * 60 * 1000
            "month" -> 30L * 24 * 60 * 60 * 1000
            else -> 24L * 60 * 60 * 1000 // day
        }

        val intervals = (overdueMs / intervalMs).toInt() + 1 // +1 because first interval starts immediately

        val penalty = when (mode) {
            "fixed" -> task.penaltyValue * intervals
            "percentage" -> (task.points * task.penaltyValue * intervals) / 100
            else -> 0
        }

        // Cap at penaltyMax (if set) and never go below 0
        val capped = if (task.penaltyMax > 0) minOf(penalty, task.penaltyMax) else penalty
        return minOf(capped, task.points)
    }

    /**
     * Calculate the next due date for a recurring task.
     *
     * Given the current time, finds the next occurrence based on recurrenceDays.
     * If frequency is "daily", returns next day.
     * If frequency is "weekly", returns the next matching weekday from recurrenceDays.
     * If frequency is "monthly", returns the same day next month.
     */
    private fun calculateNextDueDate(task: TaskResponse, afterMs: Long): Long? {
        val tz = TimeZone.currentSystemDefault()
        val afterInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(afterMs)
        val afterDateTime = afterInstant.toLocalDateTime(tz)
        val afterDate = afterDateTime.date

        // Compute epoch millis for a given date at 12:00 local time.
        fun dateToEpoch(year: Int, month: Int, day: Int): Long =
            LocalDateTime(year, month, day, 12, 0, 0).toInstant(tz).toEpochMilliseconds()

        val nextDate: LocalDate? = when (task.frequency) {
            "daily" -> afterDate.plus(1, DateTimeUnit.DAY)
            "weekly" -> {
                if (task.recurrenceDays.isEmpty()) {
                    afterDate.plus(7, DateTimeUnit.DAY)
                } else {
                    // Find the next matching day of the week
                    var candidate = afterDate.plus(1, DateTimeUnit.DAY)
                    var safety = 0
                    while (safety < 14) {
                        val dayOfWeek = candidate.dayOfWeek.ordinal + 1 // 1=Monday
                        if (dayOfWeek in task.recurrenceDays) {
                            return dateToEpoch(candidate.year, candidate.monthNumber, candidate.dayOfMonth)
                        }
                        candidate = candidate.plus(1, DateTimeUnit.DAY)
                        safety++
                    }
                    null // Should not happen
                }
            }
            "monthly" -> {
                // Next month, same day (capped at month length)
                val nextMonth = afterDate.monthNumber + 1
                val nextYear = if (nextMonth > 12) afterDate.year + 1 else afterDate.year
                val nextMonthNum = if (nextMonth > 12) nextMonth - 12 else nextMonth
                val maxDay = daysInMonth(nextYear, nextMonthNum)
                val dayOfMonth = minOf(afterDate.dayOfMonth, maxDay)
                LocalDate(nextYear, nextMonthNum, dayOfMonth)
            }
            else -> afterDate.plus(1, DateTimeUnit.DAY)
        }

        return nextDate?.let { dateToEpoch(it.year, it.monthNumber, it.dayOfMonth) }
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            else -> 30
        }
    }

    // ────────────────────────────────────────────────────────
    //  Task helpers
    // ────────────────────────────────────────────────────────

    suspend fun updateTask(
        householdId: String,
        taskId: String,
        title: String,
        description: String,
        points: Int,
        frequency: String,
        recurrenceDays: List<Int>,
        recurrenceDay: Int? = null,
        tags: List<String>,
        subtasks: List<Subtask> = emptyList(),
        penaltyMode: String?,
        penaltyValue: Int,
        penaltyInterval: String,
        penaltyMax: Int,
        assignmentRotation: List<org.taskhub.network.models.AssignmentSlot> = emptyList(),
        dueDate: Long = 0
    ) {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mutableMapOf<String, FirestoreValue>(
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "points" to FirestoreValue(integerValue = points.toString()),
            "frequency" to FirestoreValue(stringValue = frequency),
            "dueDate" to FirestoreValue(integerValue = dueDate.toString()),
            "updatedAt" to FirestoreValue(integerValue = now.toString())
        )

        // Tags as array
        fields["tags"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = tags.map { FirestoreValue(stringValue = it) }
            )
        )

        // Recurrence days as array
        fields["recurrenceDays"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = recurrenceDays.map { FirestoreValue(integerValue = it.toString()) }
            )
        )

        // Recurrence day of month (solo "monthly"); null borra el valor previo.
        fields["recurrenceDay"] = if (recurrenceDay != null) {
            FirestoreValue(integerValue = recurrenceDay.toString())
        } else {
            FirestoreValue(nullValue = "NULL_VALUE")
        }

        // Penalty configuration
        if (penaltyMode != null) {
            fields["penaltyMode"] = FirestoreValue(stringValue = penaltyMode)
            fields["penaltyValue"] = FirestoreValue(integerValue = penaltyValue.toString())
            fields["penaltyInterval"] = FirestoreValue(stringValue = penaltyInterval)
            fields["penaltyMax"] = FirestoreValue(integerValue = penaltyMax.toString())
        } else {
            fields["penaltyMode"] = FirestoreValue(nullValue = "NULL_VALUE")
            fields["penaltyValue"] = FirestoreValue(nullValue = "NULL_VALUE")
            fields["penaltyInterval"] = FirestoreValue(nullValue = "NULL_VALUE")
            fields["penaltyMax"] = FirestoreValue(nullValue = "NULL_VALUE")
        }

        // Assignment rotation as array of maps
        fields["assignmentRotation"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = assignmentRotation.map { slot ->
                    FirestoreValue(
                        mapValue = FirestoreMapValue(
                            fields = mapOf(
                                "dayOfWeek" to FirestoreValue(integerValue = slot.dayOfWeek.toString()),
                                "memberId" to FirestoreValue(stringValue = slot.memberId)
                            )
                        )
                    )
                }
            )
        )

        // Subtasks as array of maps
        fields["subtasks"] = FirestoreValue(
            arrayValue = FirestoreArrayValue(
                values = subtasks.map { st ->
                    FirestoreValue(
                        mapValue = FirestoreMapValue(
                            fields = mapOf(
                                "id" to FirestoreValue(stringValue = st.id),
                                "text" to FirestoreValue(stringValue = st.text),
                                "completed" to FirestoreValue(booleanValue = st.completed)
                            )
                        )
                    )
                }
            )
        )

        val updateMask = fields.keys.joinToString(",")

        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", updateMask)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Update only the subtasks array on a task document.
     * Used for quick toggling of individual subtask checkboxes.
     */
    suspend fun updateSubtasks(
        householdId: String,
        taskId: String,
        subtasks: List<Subtask>
    ) {
        val fields = mapOf(
            "subtasks" to FirestoreValue(
                arrayValue = FirestoreArrayValue(
                    values = subtasks.map { st ->
                        FirestoreValue(
                            mapValue = FirestoreMapValue(
                                fields = mapOf(
                                    "id" to FirestoreValue(stringValue = st.id),
                                    "text" to FirestoreValue(stringValue = st.text),
                                    "completed" to FirestoreValue(booleanValue = st.completed)
                                )
                            )
                        )
                    }
                )
            )
        )
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            parameter("updateMask.fieldPaths", "subtasks")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Delete a task document.
     */
    suspend fun deleteTask(householdId: String, taskId: String) {
        client.delete("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
        }
    }

    private fun toTaskResponse(doc: FirestoreDocumentResponse, householdId: String): TaskResponse {
        val f = doc.fields
        return TaskResponse(
            id = extractDocId(doc.name, "getTasks"),
            householdId = f["householdId"]?.stringValue ?: householdId,
            createdBy = f["createdBy"]?.stringValue ?: "",
            title = f["title"]?.stringValue ?: "",
            description = f["description"]?.stringValue ?: "",
            points = f["points"]?.integerValue?.toIntOrNull() ?: 10,
            frequency = f["frequency"]?.stringValue ?: "once",
            recurrenceDays = f["recurrenceDays"]?.arrayValue?.values
                ?.mapNotNull { it.integerValue?.toIntOrNull() } ?: emptyList(),
            recurrenceDay = f["recurrenceDay"]?.integerValue?.toIntOrNull(),
            tags = f["tags"]?.arrayValue?.values
                ?.mapNotNull { it.stringValue } ?: emptyList(),
            subtasks = f["subtasks"]?.arrayValue?.values
                ?.mapNotNull { stValue ->
                    val sf = stValue.mapValue?.fields ?: return@mapNotNull null
                    val sid = sf["id"]?.stringValue ?: return@mapNotNull null
                    val stext = sf["text"]?.stringValue ?: return@mapNotNull null
                    val scompleted = sf["completed"]?.booleanValue ?: false
                    Subtask(id = sid, text = stext, completed = scompleted)
                } ?: emptyList(),
            penaltyMode = f["penaltyMode"]?.stringValue,
            penaltyValue = f["penaltyValue"]?.integerValue?.toIntOrNull() ?: 0,
            penaltyInterval = f["penaltyInterval"]?.stringValue ?: "day",
            penaltyMax = f["penaltyMax"]?.integerValue?.toIntOrNull() ?: 0,
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            lastCompletedDate = f["lastCompletedDate"]?.integerValue?.toLongOrNull(),
            completedBy = f["completedBy"]?.stringValue,
            assignmentRotation = f["assignmentRotation"]?.arrayValue?.values
                ?.mapNotNull { slotValue ->
                    val sf = slotValue.mapValue?.fields ?: return@mapNotNull null
                    val dow = sf["dayOfWeek"]?.integerValue?.toIntOrNull() ?: return@mapNotNull null
                    val mid = sf["memberId"]?.stringValue ?: return@mapNotNull null
                    org.taskhub.network.models.AssignmentSlot(dayOfWeek = dow, memberId = mid)
                } ?: emptyList(),
            createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
            updatedAt = f["updatedAt"]?.integerValue?.toLongOrNull() ?: 0L
        )
    }

    private fun toTaskAssignmentResponse(
        doc: FirestoreDocumentResponse,
        taskId: String
    ): TaskAssignmentResponse {
        val f = doc.fields
        return TaskAssignmentResponse(
            id = extractDocId(doc.name, "getAssignments"),
            taskId = f["taskId"]?.stringValue ?: taskId,
            memberId = f["memberId"]?.stringValue ?: "",
            mandatory = f["mandatory"]?.booleanValue ?: false,
            dueDate = f["dueDate"]?.integerValue?.toLongOrNull() ?: 0L,
            status = f["status"]?.stringValue ?: "assigned",
            completedAt = f["completedAt"]?.integerValue?.toLongOrNull(),
            pointsAwarded = f["pointsAwarded"]?.integerValue?.toIntOrNull(),
            onTime = f["onTime"]?.booleanValue,
            assignedAt = f["assignedAt"]?.integerValue?.toLongOrNull() ?: 0L,
            googleEventId = f["googleEventId"]?.stringValue
        )
    }

    // ────────────────────────────────────────────────────────
    //  Comments (subcollection under households/{id}/tasks/{taskId})
    // ────────────────────────────────────────────────────────

    /** Add a comment to a task. */
    suspend fun addComment(
        householdId: String,
        taskId: String,
        authorName: String,
        text: String
    ): org.taskhub.network.models.CommentResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "authorName" to FirestoreValue(stringValue = authorName),
            "text" to FirestoreValue(stringValue = text),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/tasks/$taskId/comments"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "addComment")
        return org.taskhub.network.models.CommentResponse(id, authorName, text, now)
    }

    /** List comments for a task. */
    suspend fun getComments(
        householdId: String,
        taskId: String
    ): List<org.taskhub.network.models.CommentResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/tasks/$taskId/comments"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { doc ->
            val f = doc.fields
            org.taskhub.network.models.CommentResponse(
                id = extractDocId(doc.name, "getComments"),
                authorName = f["authorName"]?.stringValue ?: "",
                text = f["text"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }
    }

    // ────────────────────────────────────────────────────────
    //  Messages (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Send a chat message to a household. */
    suspend fun sendMessage(
        householdId: String,
        memberId: String,
        authorName: String,
        text: String
    ): org.taskhub.network.models.MessageResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "memberId" to FirestoreValue(stringValue = memberId),
            "authorName" to FirestoreValue(stringValue = authorName),
            "text" to FirestoreValue(stringValue = text),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/messages"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "sendMessage")
        return org.taskhub.network.models.MessageResponse(id, memberId, authorName, text, now)
    }

    /** List chat messages for a household, oldest first. */
    suspend fun getMessages(householdId: String): List<org.taskhub.network.models.MessageResponse> {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/messages"
        ) {
            tryAuthOrApiKey()
        }.body()

        return response.documents.map { doc ->
            val f = doc.fields
            org.taskhub.network.models.MessageResponse(
                id = extractDocId(doc.name, "getMessages"),
                memberId = f["memberId"]?.stringValue ?: "",
                authorName = f["authorName"]?.stringValue ?: "",
                text = f["text"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }.sortedBy { it.createdAt }
    }

    // ────────────────────────────────────────────────────────
    //  Notifications (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Create a notification document for a member. */
    suspend fun createNotification(
        householdId: String,
        memberId: String,
        taskId: String,
        title: String,
        message: String
    ): NotificationResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        val fields = mapOf(
            "memberId" to FirestoreValue(stringValue = memberId),
            "taskId" to FirestoreValue(stringValue = taskId),
            "title" to FirestoreValue(stringValue = title),
            "message" to FirestoreValue(stringValue = message),
            "createdAt" to FirestoreValue(integerValue = now.toString()),
            "read" to FirestoreValue(booleanValue = false)
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/notifications"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createNotification")
        return NotificationResponse(id, memberId, taskId, title, message, now, read = false)
    }

    /** Get all notifications for a household. */
    suspend fun getNotifications(householdId: String): List<NotificationResponse> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/notifications"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            NotificationResponse(
                id = extractDocId(doc.name, "getNotifications"),
                memberId = f["memberId"]?.stringValue ?: "",
                taskId = f["taskId"]?.stringValue ?: "",
                title = f["title"]?.stringValue ?: "",
                message = f["message"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L,
                read = f["read"]?.booleanValue ?: false
            )
        }
    }

    /** Mark a notification as read. */
    suspend fun markNotificationRead(householdId: String, notificationId: String) {
        val fields = mapOf(
            "read" to FirestoreValue(booleanValue = true)
        )
        client.patch(
            "$baseUrl/households/$householdId/notifications/$notificationId"
        ) {
            withAuth()
            parameter("updateMask.fieldPaths", "read")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    // ────────────────────────────────────────────────────────
    //  Rewards (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** List all rewards for a household. */
    suspend fun getRewards(householdId: String): List<RewardResponse> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/rewards"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            RewardResponse(
                id = extractDocId(doc.name, "getRewards"),
                householdId = f["householdId"]?.stringValue ?: householdId,
                title = f["title"]?.stringValue ?: "",
                description = f["description"]?.stringValue ?: "",
                cost = f["cost"]?.integerValue?.toIntOrNull() ?: 0,
                icon = f["icon"]?.stringValue ?: "🎁",
                createdBy = f["createdBy"]?.stringValue ?: "",
                createdAt = f["createdAt"]?.integerValue?.toLongOrNull() ?: 0L
            )
        }
    }

    /** Create a reward. Requires auth (write). */
    suspend fun createReward(
        householdId: String,
        title: String,
        description: String,
        cost: Int,
        icon: String,
        createdBy: String
    ): RewardResponse {
        val now = Clock.System.now().toEpochMilliseconds()

        val fields = mapOf(
            "householdId" to FirestoreValue(stringValue = householdId),
            "title" to FirestoreValue(stringValue = title),
            "description" to FirestoreValue(stringValue = description),
            "cost" to FirestoreValue(integerValue = cost.toString()),
            "icon" to FirestoreValue(stringValue = icon),
            "createdBy" to FirestoreValue(stringValue = createdBy),
            "createdAt" to FirestoreValue(integerValue = now.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/rewards"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        val id = extractDocId(response.name, "createReward")
        return RewardResponse(id, householdId, title, description, cost, icon, createdBy, now)
    }

    /** Delete a reward. Requires auth (write). */
    suspend fun deleteReward(householdId: String, rewardId: String) {
        client.delete("$baseUrl/households/$householdId/rewards/$rewardId") {
            withAuth()
        }
    }

    /** Redeem a reward: subtract points from member, record redemption. Requires auth (write). */
    suspend fun redeemReward(
        householdId: String,
        rewardId: String,
        memberId: String,
        pointsSpent: Int
    ): RewardRedemption {
        val now = Clock.System.now().toEpochMilliseconds()

        // 1. Guardar primero el registro de canje: si el paso 2 (descontar
        //    puntos) falla a mitad de camino, queda un registro auditable en
        //    vez de puntos perdidos sin ningún rastro de en qué se gastaron.
        val fields = mapOf(
            "rewardId" to FirestoreValue(stringValue = rewardId),
            "memberId" to FirestoreValue(stringValue = memberId),
            "redeemedAt" to FirestoreValue(integerValue = now.toString()),
            "pointsSpent" to FirestoreValue(integerValue = pointsSpent.toString())
        )

        val response: FirestoreDocumentResponse = client.post(
            "$baseUrl/households/$householdId/rewardRedemptions"
        ) {
            withAuth()
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }.body()

        // 2. Descontar los puntos del miembro.
        addMemberPoints(householdId, memberId, -pointsSpent)

        val id = extractDocId(response.name, "redeemReward")
        return RewardRedemption(id, rewardId, memberId, now, pointsSpent)
    }

    /** Get all reward redemptions for a household. */
    suspend fun getRewardRedemptions(householdId: String): List<RewardRedemption> = orDefault(emptyList()) {
        val response: FirestoreListResponse = client.get(
            "$baseUrl/households/$householdId/rewardRedemptions"
        ) {
            tryAuthOrApiKey()
        }.body()

        response.documents.map { doc ->
            val f = doc.fields
            RewardRedemption(
                id = extractDocId(doc.name, "getRewardRedemptions"),
                rewardId = f["rewardId"]?.stringValue ?: "",
                memberId = f["memberId"]?.stringValue ?: "",
                redeemedAt = f["redeemedAt"]?.integerValue?.toLongOrNull() ?: 0L,
                pointsSpent = f["pointsSpent"]?.integerValue?.toIntOrNull() ?: 0
            )
        }
    }

    // ────────────────────────────────────────────────────────
    //  Request helpers
    // ────────────────────────────────────────────────────────

    /**
     * Tries Bearer auth first; falls back to API key parameter.
     * Used for read operations where API key alone might suffice.
     */
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() {
        try {
            ensureAuth()
            bearerToken?.let { header("Authorization", "Bearer $it") }
        } catch (_: Exception) {
            // Auth failed — fall back to API key for read-only access
            parameter("key", apiKey)
        }
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    /**
     * Ejecuta [block] y devuelve [default] ante cualquier fallo NO fatal (subcolección
     * que aún no existe, red, etc.), pero relanza [CancellationException] para no
     * romper la cancelación cooperativa de la corrutina (p.ej. al salir de la
     * pantalla mientras la petición está en curso).
     */
    private suspend inline fun <T> orDefault(default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            default
        }
    }

    /** Ver [FirestoreParsers.extractDocId] — extraído para ser testable sin I/O. */
    private fun extractDocId(resourceName: String, operation: String): String =
        FirestoreParsers.extractDocId(resourceName, operation)

    private fun toHouseholdResponse(
        doc: FirestoreDocumentResponse,
        knownId: String? = null,
        operation: String = "getHousehold"
    ): HouseholdResponse = FirestoreParsers.toHouseholdResponse(doc, knownId, operation)

    private fun toMemberResponse(
        doc: FirestoreDocumentResponse,
        householdId: String,
        operation: String = "getMembers"
    ): MemberResponse = FirestoreParsers.toMemberResponse(doc, householdId, operation)

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars[secureRandomInt(chars.length)] }.joinToString("")
    }

    companion object {
        /** Firebase Web API Key for task-hub-62f98 (Firebase Console → Project Settings → General). */
        const val DEFAULT_API_KEY = "AIzaSyD5Xo11SqvysWRgEFv_91rBjYuFIq93lV8"

        /** Reintentos ante conflicto de concurrencia optimista (ver [addMemberPoints]/[addMemberAchievement]). */
        private const val OPTIMISTIC_WRITE_MAX_RETRIES = 3
    }
}
