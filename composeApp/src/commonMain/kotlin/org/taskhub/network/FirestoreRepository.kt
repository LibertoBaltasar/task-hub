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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
// NOTA: FormDataContent/Parameters (refresh de token) y errorParsingJson/HttpClient
// ahora viven en FirestoreClient (ver fase 1 del refactor, docs/refactor-arquitectura-2026-08-31.md).
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
    private val settingsStore: SettingsStore,
    private val firestoreClient: FirestoreClient = FirestoreClient(apiKey, settingsStore)
) {
    private val baseUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    // ── Cliente HTTP + auth de bajo nivel (ver FirestoreClient, fase 1 del
    // refactor). `client` se mantiene como alias local para no tocar los ~60
    // call-sites internos (`client.get/post/patch/delete`) de este archivo.
    private val client = firestoreClient.client

    // ── Repos de dominio (fase 2 del refactor) — FirestoreRepository actúa
    // como facade temporal mientras dura la migración (fase 3).
    private val notificationRepository = NotificationRepository(baseUrl, firestoreClient)
    private val rewardsRepository = RewardsRepository(baseUrl, firestoreClient)
    private val taskRepository = TaskRepository(baseUrl, firestoreClient, taskCache, notificationRepository)
    private val householdRepository = HouseholdRepository(baseUrl, firestoreClient, taskCache) { getLocalId() }

    // Reintentos ante conflicto de concurrencia optimista (ver `addMemberPoints`/
    // `addMemberAchievement`) — alias local, mismo motivo que `client` arriba.
    private val OPTIMISTIC_WRITE_MAX_RETRIES = FirestoreClient.OPTIMISTIC_WRITE_MAX_RETRIES

    // ── Miembro actual (single source of truth, ver resolveCurrentMember) ──
    // Memoiza el resultado por hogar: sin esto, cada ScreenModel que necesita
    // saber "quién soy en este hogar" (TaskScreenModel, CalendarSyncManager,
    // HouseholdScreen) repite la misma resolución de identidad (+ getMembers)
    // de forma independiente y podría, en teoría, no coincidir si la lista de
    // miembros cambia entre una llamada y otra dentro de la misma sesión.
    private val currentMemberMutex = Mutex()
    private val currentMemberCache = mutableMapOf<String, String>()

    // ────────────────────────────────────────────────────────
    //  Auth
    // ────────────────────────────────────────────────────────

    /**
     * Ver [FirestoreClient.ensureAuth] — delegado tal cual (wrapper para no
     * tocar los call-sites internos `ensureAuth()` de este archivo).
     */
    private suspend fun ensureAuth() = firestoreClient.ensureAuth()

    /**
     * Devuelve el UID del usuario actual (anónimo o Google). Cae al UID persistido
     * si aún no se ha autenticado en esta sesión, para que esté disponible antes
     * de la primera llamada de red (p.ej. al crear el miembro "Yo" del Personal).
     */
    fun getLocalId(): String? =
        firestoreClient.cachedLocalId ?: settingsStore.getGoogleUid() ?: settingsStore.getAnonymousUid()

    /**
     * True si el usuario actual es el owner del hogar (comparación de
     * [getLocalId] con `household.ownerId`) — igual que `isTrusted(hid)` en
     * `firestore.rules`. Se usaba duplicado en 3 sitios (`HouseholdScreen`,
     * `TaskDetailScreen`, `RewardListScreen`), cada uno inyectando
     * `FirestoreRepository` directamente para repetir la misma comparación.
     * false ante cualquier fallo de red (best-effort, igual que el código que
     * sustituye).
     */
    suspend fun isHouseholdOwner(householdId: String): Boolean {
        val localId = getLocalId() ?: return false
        val household = try {
            getHousehold(householdId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return localId == household.ownerId
    }

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
            firestoreClient.cachedLocalId
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
        firestoreClient.setAuthState(idToken, localId, now + (expiresIn * 1000) - 300_000)

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
            updateMaskFieldPaths("fcmToken", "fcmTokenUpdatedAt")
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fallo de transporte (sin red): no se llegó a Firestore.
            false
        }
    }

    /**
     * Ver [FirestoreClient.withAuth]/[FirestoreClient.updateMaskFieldPaths] —
     * delegados tal cual (wrappers para no tocar los ~60 call-sites internos
     * de este archivo que ya invocan `withAuth()`/`updateMaskFieldPaths(...)`
     * a secas dentro de un bloque `client.get/post/patch/delete { ... }`).
     */
    private suspend fun HttpRequestBuilder.withAuth() = with(firestoreClient) { withAuth() }

    private fun HttpRequestBuilder.updateMaskFieldPaths(vararg fields: String) =
        with(firestoreClient) { updateMaskFieldPaths(*fields) }

    private fun HttpRequestBuilder.updateMaskFieldPaths(fields: Collection<String>) =
        with(firestoreClient) { updateMaskFieldPaths(fields) }

    // ────────────────────────────────────────────────────────
    //  Households
    // ────────────────────────────────────────────────────────

    /** Create a household (auto-generated doc ID). Requires auth (write). */
    suspend fun createHousehold(name: String, isPersonal: Boolean = false): HouseholdResponse =
        householdRepository.createHousehold(name, isPersonal)

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
    suspend fun getOrCreatePersonalHousehold(): HouseholdResponse = householdRepository.getOrCreatePersonalHousehold()

    /** ID determinista del espacio Personal para una identidad (UID) dada. */
    fun personalHouseholdId(uid: String): String = householdRepository.personalHouseholdId(uid)

    /**
     * Get a household by id. Falls back to local cache on network/5xx failures.
     *
     * A 404/403 from Firestore is a DEFINITIVE signal — the household was deleted
     * or we lost access to it — so it must NOT fall back to the stale cache (that
     * would keep showing a "ghost" household forever). Callers that need to prune
     * local state should catch [FirestoreException] and check [FirestoreException.statusCode].
     */
    suspend fun getHousehold(id: String): HouseholdResponse = householdRepository.getHousehold(id)

    /**
     * Reconcilia los hogares guardados localmente en [store] contra Firestore
     * (fuente de verdad). Ver [HouseholdRepository.reconcileHouseholds] para el
     * detalle de la política de poda (movida tal cual, sin cambios).
     */
    suspend fun reconcileHouseholds(store: HouseholdStore): List<SavedHousehold> =
        householdRepository.reconcileHouseholds(store)

    /** Batch-fetch multiple households by their document IDs (en paralelo). */
    suspend fun getHouseholds(ids: List<String>): List<HouseholdResponse> = householdRepository.getHouseholds(ids)

    /** Delete a household document. Does NOT cascade-delete subcollections (members, tasks)
     *  — those become orphaned but harmless. Requires auth (write). */
    suspend fun deleteHousehold(householdId: String) {
        client.delete("$baseUrl/households/$householdId") {
            withAuth()
        }
        taskCache.clearHousehold(householdId)
        currentMemberCache.remove(householdId)
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
        } catch (e: CancellationException) {
            throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No crítico: si el doc ya no existe, seguimos.
            }
        }
        if (deleted.isNotEmpty()) {
            taskCache.clearMembers(householdId)
            currentMemberCache.remove(householdId)
        }

        val remaining = try {
            getMembers(householdId)
        } catch (e: CancellationException) {
            throw e
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
    suspend fun joinHousehold(inviteCode: String): HouseholdResponse = householdRepository.joinHousehold(inviteCode)

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
        taskCache.clearMembers(householdId)

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
     *
     * Memoizada por hogar (single source of truth, ver [currentMemberCache]):
     * llamadas repetidas dentro de la misma sesión devuelven el mismo valor
     * sin repetir la resolución de identidad ni la llamada a [getMembers].
     * La entrada se invalida al abandonar el hogar ([leaveHousehold]) o
     * borrarlo ([deleteHousehold]).
     */
    suspend fun resolveCurrentMember(householdId: String): String {
        currentMemberCache[householdId]?.let { return it }
        return currentMemberMutex.withLock {
            currentMemberCache[householdId]?.let { return@withLock it }
            val resolved = resolveCurrentMemberUncached(householdId)
            // No se memoiza un resultado vacío: sería un fallo transitorio
            // (getMembers y createMember fallaron), no una identidad real.
            if (resolved.isNotBlank()) {
                currentMemberCache[householdId] = resolved
            }
            resolved
        }
    }

    private suspend fun resolveCurrentMemberUncached(householdId: String): String {
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
            updateMaskFieldPaths("leftAt")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
        taskCache.clearMembers(householdId)

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
            updateMaskFieldPaths("role")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
        taskCache.clearMembers(householdId)
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
            updateMaskFieldPaths(fields.keys)
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
            updateMaskFieldPaths("currentStreak", "bestStreak", "lastStreakDate")
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
                    updateMaskFieldPaths("totalPoints")
                    current.updateTime?.let { parameter("currentDocument.updateTime", it) }
                    contentType(ContentType.Application.Json)
                    setBody(FirestoreDocument(mapOf("totalPoints" to FirestoreValue(integerValue = newTotal.toString()))))
                }
                taskCache.clearMembers(householdId)
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

        val toMember = getMembers(householdId).find { it.id == toMemberId }
            ?: return AppreciateResult.Error(AppreciateErrorReason.MEMBER_NOT_FOUND)

        // Consumir presupuesto ANTES de acuñar puntos (si la red falla entre las
        // dos escrituras, es mejor que el receptor se quede sin acreditar,
        // recuperable reintentando, que dejar que el emisor supere el tope
        // semanal reintentando una acuñación que ya se completó) — y con
        // concurrencia optimista (`currentDocument.updateTime` + reintento, igual
        // que [addMemberPoints]): sin esto, dos "agradecer" casi simultáneos del
        // mismo emisor podrían validar ambos contra el mismo presupuesto stale y
        // saltarse el tope de 50 pts/semana. El perdedor de la carrera reintenta
        // automáticamente contra el valor fresco (hasta [OPTIMISTIC_WRITE_MAX_RETRIES]
        // veces); si el tope ya estaba agotado por el ganador, ve LIMIT_EXCEEDED.
        val docUrl = "$baseUrl/households/$householdId/members/$fromMemberId"
        repeat(OPTIMISTIC_WRITE_MAX_RETRIES) { attempt ->
            val current: FirestoreDocumentResponse = try {
                client.get(docUrl) { withAuth() }.body()
            } catch (e: FirestoreException) {
                if (e.statusCode == 404) return AppreciateResult.Error(AppreciateErrorReason.MEMBER_NOT_FOUND)
                throw e
            }
            val fromMember = FirestoreParsers.toMemberResponse(current, householdId, "appreciateMember")
            val now = Clock.System.now().toEpochMilliseconds()
            val budget = currentAppreciationBudget(fromMember, now)
            PointsRules.validateAppreciateLimit(amount, budget)?.let {
                return AppreciateResult.Error(it.toRepoReason())
            }
            val newGiven = budget.given + amount
            val fields = mapOf(
                "appreciationGiven" to FirestoreValue(integerValue = newGiven.toString()),
                "appreciationWeekStart" to FirestoreValue(integerValue = budget.weekStart.toString())
            )
            try {
                client.patch(docUrl) {
                    withAuth()
                    updateMaskFieldPaths("appreciationGiven", "appreciationWeekStart")
                    current.updateTime?.let { parameter("currentDocument.updateTime", it) }
                    contentType(ContentType.Application.Json)
                    setBody(FirestoreDocument(fields))
                }
                taskCache.clearMembers(householdId)
                // Acuñar: el receptor gana puntos sin que se le resten al que agradece.
                addMemberPoints(householdId, toMemberId, amount)
                return AppreciateResult.Ok(
                    remaining = PointsRules.WEEKLY_APPRECIATION_BUDGET - newGiven,
                    receptorNewTotal = toMember.totalPoints + amount
                )
            } catch (e: FirestoreException) {
                val isConflict = e.code == "FAILED_PRECONDITION" || e.code == "ABORTED"
                if (!isConflict || attempt == OPTIMISTIC_WRITE_MAX_RETRIES - 1) throw e
                // Otro escritor ganó la carrera: reintentar con el presupuesto fresco.
            }
        }
        throw IllegalStateException("appreciateMember: reintentos de concurrencia agotados")
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
                    updateMaskFieldPaths("unlocked", "updatedAt")
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

    // Tasks CRUD + history + assignments — delegado en TaskRepository (fase
    // 2.3 del refactor), salvo completeTask/completeAssignment/
    // reassignTaskCompletion (capa de puntos, se quedan hasta la fase Member).

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
    ): TaskResponse = taskRepository.createTask(
        householdId, createdBy, title, description, points, frequency, recurrenceDays,
        recurrenceDay, tags, subtasks, penaltyMode, penaltyValue, penaltyInterval, penaltyMax,
        dueDate, assignmentRotation
    )

    suspend fun getTasks(householdId: String): List<TaskResponse> = taskRepository.getTasks(householdId)

    /** Get a single task by id. Used where only one task is needed (avoids an N+1 full-list fetch). */
    suspend fun getTask(householdId: String, taskId: String): TaskResponse = taskRepository.getTask(householdId, taskId)

    /** Resultado de [completeTask]: puntos realmente otorgados (tras penalización) y puntualidad. */
    data class TaskCompletionResult(val completedAt: Long, val pointsAwarded: Int, val onTime: Boolean)

    /**
     * Otro dispositivo modificó el documento de la tarea (típicamente
     * completándola también) entre que [completeTask] leyó su estado y trató
     * de marcarla como completada. A diferencia de [addMemberPoints] (donde
     * reintentar con el valor fresco es seguro porque sumar es conmutativo),
     * completar una tarea NO es idempotente: reintentar automáticamente
     * otorgaría los puntos dos veces. Política elegida: el perdedor de la
     * carrera ve este error (mapeado a `TaskActionState.Error` en
     * `TaskScreenModel`, mismo tratamiento que cualquier otro fallo de red) y
     * debe recargar/reintentar a mano — nunca se le otorgan puntos.
     */
    class TaskCompletionConflictException(message: String) : Exception(message)

    /**
     * Otro dispositivo modificó la asignación entre que [completeAssignment]
     * la leyó y trató de marcarla como completada. Mismo motivo y política
     * que [TaskCompletionConflictException] (no reintentar automáticamente:
     * completar no es idempotente) — antes `completeAssignment` no tenía
     * ninguna protección aquí, así que dos dispositivos completando la misma
     * asignación casi a la vez duplicaban puntos/historial y podían crear DOS
     * asignaciones distintas para la siguiente ocurrencia (cadena bifurcada).
     */
    class AssignmentCompletionConflictException(message: String) : Exception(message)

    /**
     * Mark a task as completed today. Sets lastCompletedDate, awards points
     * (con penalización por retraso si `task.nextDueAt`/`dueDate`/`penaltyMode`
     * aplican — ver [resolveCompletionOutcome]), records history, sincroniza la
     * asignación de este ciclo (si existía) como completada y regenera la de
     * la siguiente ocurrencia respetando `assignmentRotation` (ver
     * [regenerateNextAssignment] — unificado con [completeAssignment], antes
     * solo ese flujo regeneraba, y siempre ignorando la rotación).
     *
     * Concurrencia optimista sobre el documento de la tarea (`currentDocument.
     * updateTime` como precondition del primer PATCH, sin reintento — ver
     * [TaskCompletionConflictException] para la política de conflicto):
     * evita que dos dispositivos completando la misma tarea casi a la vez
     * dupliquen puntos/historial.
     *
     * NO es atómica de extremo a extremo más allá de ese primer PATCH (las
     * escrituras siguientes siguen siendo HTTP secuenciales): un fallo de red
     * a mitad de secuencia deja estado parcial, mitigado por la guarda de
     * reentrancia de `TaskScreenModel` pero no eliminado. Evaluado y
     * descartado usar el endpoint `:commit` con `fieldTransforms` para
     * hacerlo transaccional — ver `docs/atomicidad-commit-pendiente.md` para
     * el motivo (no se puede verificar el payload contra la API real en este
     * entorno) y los pasos para hacerlo con seguridad en el futuro.
     */
    suspend fun completeTask(
        householdId: String,
        taskId: String,
        memberId: String,
        task: TaskResponse
    ): TaskCompletionResult {
        val now = Clock.System.now().toEpochMilliseconds()
        // nextDueAt (si existe) es la medianoche del día programado, no una
        // fecha límite con hora real (ver KDoc de RecurrenceRules.endOfDueDay)
        // — sin este ajuste, completar el mismo día programado (lo normal)
        // se marcaría siempre como "tarde".
        val effectiveDueDate = if (task.frequency == "once") {
            task.dueDate
        } else {
            task.nextDueAt?.let { RecurrenceRules.endOfDueDay(it) } ?: task.dueDate
        }
        val outcome = resolveCompletionOutcome(task, effectiveDueDate, now)
        val nextDueDate = calculateNextDueDate(task, now)

        // 1. Update lastCompletedDate + completedBy (+ nextDueAt si es
        //    recurrente) on the task. completedBy registra QUIÉN marcó hecho
        //    (quien recibe los puntos), al margen de quién esté asignado.
        val docUrl = "$baseUrl/households/$householdId/tasks/$taskId"
        val current: FirestoreDocumentResponse = client.get(docUrl) { withAuth() }.body()
        val fields = mutableMapOf(
            "lastCompletedDate" to FirestoreValue(integerValue = now.toString()),
            "completedBy" to FirestoreValue(stringValue = memberId)
        )
        if (task.frequency != "once") {
            fields["nextDueAt"] = nextDueAtValue(nextDueDate)
        }
        try {
            client.patch(docUrl) {
                withAuth()
                updateMaskFieldPaths(fields.keys)
                current.updateTime?.let { parameter("currentDocument.updateTime", it) }
                contentType(ContentType.Application.Json)
                setBody(FirestoreDocument(fields))
            }
        } catch (e: FirestoreException) {
            if (e.code == "FAILED_PRECONDITION" || e.code == "ABORTED") {
                throw TaskCompletionConflictException(
                    "La tarea se modificó en otro dispositivo justo antes de completarla. Vuelve a intentarlo."
                )
            }
            throw e
        }
        taskCache.clearTasks(householdId)

        // 2. Add points to the member
        addMemberPoints(householdId, memberId, outcome.pointsAwarded)

        // 3. Save task history record
        saveTaskHistory(
            householdId = householdId,
            taskId = taskId,
            memberId = memberId,
            points = outcome.pointsAwarded,
            completedAt = now,
            onTime = outcome.onTime
        )

        // 4. Sincronizar la asignación de este ciclo (si la había) como
        //    completada — antes de regenerar, para no dejar ambigüedad entre
        //    "la que se acaba de completar" y "la de la siguiente ocurrencia"
        //    cuando las dos coexisten con status="assigned" (ver KDoc de
        //    [regenerateNextAssignment]). No otorga puntos (ya se otorgaron
        //    arriba).
        val assignments = try {
            getAssignments(householdId, taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val existingAssignment = assignments.find { it.memberId == memberId && it.status == "assigned" }
        if (existingAssignment != null) {
            try {
                markAssignmentCompleted(householdId, taskId, existingAssignment.id, now, outcome.pointsAwarded, outcome.onTime)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }

        // 5. Regenerar la asignación de la siguiente ocurrencia (recurrentes).
        if (task.frequency != "once") {
            regenerateNextAssignment(
                householdId, taskId, task, memberId,
                existingAssignment?.mandatory ?: false, nextDueDate, assignments
            )
        }

        return TaskCompletionResult(now, outcome.pointsAwarded, outcome.onTime)
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
    ) = taskRepository.revertTaskCompletion(householdId, taskId, previousLastCompletedDate, previousCompletedBy)

    /** Save a task completion record to Firestore taskHistory subcollection. */
    suspend fun saveTaskHistory(
        householdId: String,
        taskId: String,
        memberId: String,
        points: Int,
        completedAt: Long,
        onTime: Boolean
    ) = taskRepository.saveTaskHistory(householdId, taskId, memberId, points, completedAt, onTime)

    /** Get all task history records for a household. */
    suspend fun getTaskHistory(householdId: String): List<TaskHistoryResponse> = taskRepository.getTaskHistory(householdId)

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
            updateMaskFieldPaths("completedBy")
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
        taskCache.clearTasks(householdId)

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
        } catch (e: CancellationException) {
            throw e
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
            updateMaskFieldPaths("memberId")
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No crítico: si ya no existe (o falla, p.ej. sin permiso de borrado
            // de un registro ajeno bajo firestore.rules v4), el undo de
            // puntos/racha sigue en pie.
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
    ): List<TaskAssignmentResponse> = taskRepository.assignTask(householdId, taskId, memberIds, mandatory, dueDate, taskTitle)

    /** Get all assignments for a specific task. */
    suspend fun getAssignments(householdId: String, taskId: String): List<TaskAssignmentResponse> =
        taskRepository.getAssignments(householdId, taskId)

    /** Borra todas las asignaciones de una tarea (para reasignar al editar). */
    suspend fun deleteAssignments(householdId: String, taskId: String) =
        taskRepository.deleteAssignments(householdId, taskId)

    /**
     * Sustituye las asignaciones de una tarea por unas nuevas (usado al editar
     * la tarea desde [EditTaskScreen]).
     *
     * Mitigación de atomicidad (ver `docs/atomicidad-commit-pendiente.md`,
     * sección `updateTask`): antes esta operación era `deleteAssignments` +
     * `assignTask` como dos pasos independientes en el caller — si la creación
     * de las nuevas asignaciones fallaba a mitad de camino (p. ej. tras crear
     * la asignación de 2 de 3 miembros), la tarea ya se había quedado sin
     * ninguna asignación previa, así que el resultado era "tarea con solo 2
     * asignaciones" en el mejor caso o "sin ninguna" si fallaba en el primer
     * miembro. Aquí se invierte el orden: se crean las asignaciones nuevas
     * PRIMERO y solo se borran las antiguas si esa creación no lanzó. Si el
     * paso de creación falla, la tarea conserva sus asignaciones previas
     * (estado recuperable) en vez de quedarse sin ninguna. Sigue sin ser
     * atómico de extremo a extremo (un fallo justo en el borrado de las
     * antiguas puede dejar antiguas + nuevas duplicadas, un estado peor que
     * "sin cambios" pero mejor que "sin asignaciones").
     */
    suspend fun replaceAssignments(
        householdId: String,
        taskId: String,
        memberIds: List<String>,
        mandatory: Boolean,
        dueDate: Long,
        taskTitle: String = ""
    ): List<TaskAssignmentResponse> =
        taskRepository.replaceAssignments(householdId, taskId, memberIds, mandatory, dueDate, taskTitle)

    /** Get all assignments across all tasks for a household (peticiones en paralelo). */
    suspend fun getAllAssignments(householdId: String): List<TaskAssignmentResponse> =
        taskRepository.getAllAssignments(householdId)

    /**
     * Marca el documento de una asignación como completada (sin tocar puntos
     * del miembro). Si [expectedUpdateTime] no es null, se usa como
     * `currentDocument.updateTime` (precondition de concurrencia optimista) —
     * usado por [completeAssignment]; los demás callers (sync interno de
     * [completeTask]) no lo necesitan porque ya ganaron la carrera al superar
     * la precondition del documento de la tarea.
     */
    private suspend fun markAssignmentCompleted(
        householdId: String,
        taskId: String,
        assignmentId: String,
        completedAt: Long,
        pointsAwarded: Int,
        onTime: Boolean,
        expectedUpdateTime: String? = null
    ) {
        val fields = mapOf<String, FirestoreValue>(
            "status" to FirestoreValue(stringValue = "completed"),
            "completedAt" to FirestoreValue(integerValue = completedAt.toString()),
            "pointsAwarded" to FirestoreValue(integerValue = pointsAwarded.toString()),
            "onTime" to FirestoreValue(booleanValue = onTime)
        )
        client.patch(
            "$baseUrl/households/$householdId/tasks/$taskId/assignments/$assignmentId"
        ) {
            withAuth()
            updateMaskFieldPaths("status", "completedAt", "pointsAwarded", "onTime")
            expectedUpdateTime?.let { parameter("currentDocument.updateTime", it) }
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(fields))
        }
    }

    /**
     * Complete a task assignment. Calculates penalty if overdue, handles recurrence.
     * Returns the updated assignment.
     *
     * Concurrencia optimista sobre el documento de la ASIGNACIÓN
     * (`currentDocument.updateTime`, mismo patrón que [completeTask] tiene
     * sobre el documento de la tarea desde una ronda anterior) — antes esta
     * función no tenía ninguna protección: dos dispositivos completando la
     * misma asignación casi a la vez duplicaban puntos/historial y podían
     * generar DOS asignaciones distintas para la siguiente ocurrencia (cadena
     * bifurcada). Ver [AssignmentCompletionConflictException] para la política
     * de conflicto (idéntica a la de [completeTask]: el perdedor no reintenta
     * solo, debe recargar y volver a intentarlo).
     */
    suspend fun completeAssignment(
        householdId: String,
        taskId: String,
        task: TaskResponse,
        assignmentId: String,
        assignment: TaskAssignmentResponse
    ): TaskAssignmentResponse {
        val now = Clock.System.now().toEpochMilliseconds()
        // assignment.dueDate para tareas recurrentes es medianoche del día
        // programado (viene de nextOccurrence vía regenerateNextAssignment),
        // no una hora límite real — mismo ajuste que en completeTask (ver
        // RecurrenceRules.endOfDueDay). Las "once" sí tienen una hora real
        // elegida por el usuario, se usan tal cual.
        val effectiveDueDate = when {
            task.frequency == "once" -> assignment.dueDate
            assignment.dueDate == 0L -> 0L
            else -> RecurrenceRules.endOfDueDay(assignment.dueDate)
        }
        val outcome = resolveCompletionOutcome(task, effectiveDueDate, now)
        val onTime = outcome.onTime
        val pointsAwarded = outcome.pointsAwarded

        // Update assignment — con precondition de concurrencia optimista.
        val assignmentUrl = "$baseUrl/households/$householdId/tasks/$taskId/assignments/$assignmentId"
        val currentAssignmentDoc: FirestoreDocumentResponse = client.get(assignmentUrl) { withAuth() }.body()
        try {
            markAssignmentCompleted(
                householdId, taskId, assignmentId, now, pointsAwarded, onTime,
                expectedUpdateTime = currentAssignmentDoc.updateTime
            )
        } catch (e: FirestoreException) {
            if (e.code == "FAILED_PRECONDITION" || e.code == "ABORTED") {
                throw AssignmentCompletionConflictException(
                    "Esta asignación se completó en otro dispositivo justo antes. Vuelve a intentarlo."
                )
            }
            throw e
        }

        // Award points + persist history + sincronizar completedBy/lastCompletedDate
        // (+ nextDueAt si es recurrente) en la propia tarea, igual que
        // [completeTask] — antes esta función solo marcaba la asignación como
        // completada sin que los puntos llegaran al saldo real del miembro
        // (bug crítico: la UI mostraba "+N pts" que nunca se sumaban a
        // totalPoints ni podían canjearse por recompensas).
        addMemberPoints(householdId, assignment.memberId, pointsAwarded)
        saveTaskHistory(
            householdId = householdId,
            taskId = taskId,
            memberId = assignment.memberId,
            points = pointsAwarded,
            completedAt = now,
            onTime = onTime
        )
        val nextDueDate = calculateNextDueDate(task, now)
        val taskFields = mutableMapOf(
            "lastCompletedDate" to FirestoreValue(integerValue = now.toString()),
            "completedBy" to FirestoreValue(stringValue = assignment.memberId)
        )
        if (task.frequency != "once") {
            taskFields["nextDueAt"] = nextDueAtValue(nextDueDate)
        }
        client.patch("$baseUrl/households/$householdId/tasks/$taskId") {
            withAuth()
            updateMaskFieldPaths(taskFields.keys)
            contentType(ContentType.Application.Json)
            setBody(FirestoreDocument(taskFields))
        }
        taskCache.clearTasks(householdId)

        // Handle recurrence: create next assignment respetando assignmentRotation
        // (ver [regenerateNextAssignment] — unificado con [completeTask]; antes
        // esta función siempre reasignaba al mismo miembro que acababa de
        // completarla, ignorando la rotación por completo).
        if (task.frequency != "once") {
            regenerateNextAssignment(
                householdId, taskId, task, assignment.memberId, assignment.mandatory, nextDueDate
            )
        }

        return assignment.copy(
            status = "completed",
            completedAt = now,
            pointsAwarded = pointsAwarded,
            onTime = onTime
        )
    }

    /**
     * Crea (o renueva) la asignación de la SIGUIENTE ocurrencia de una tarea
     * recurrente, respetando `assignmentRotation` si está configurada (si no,
     * mantiene al miembro que acaba de completarla — comportamiento legado,
     * ver [RecurrenceRules.resolveRotationAssignee]).
     *
     * Único punto compartido por [completeTask] y [completeAssignment] — antes
     * solo `completeAssignment` regeneraba la siguiente asignación (dejando
     * "huérfana" la UI de asignaciones y sin sincronizar Google Calendar a
     * partir del segundo ciclo cuando se completaba desde la lista principal),
     * y lo hacía siempre al mismo miembro, ignorando la rotación.
     *
     * No hace nada si [nextDueDate] es null (tarea "once", sin siguiente
     * ocurrencia). Deduplica contra una asignación "assigned" ya existente
     * para el mismo miembro+fecha límite (p.ej. si ya se pasó [existingAssignments]
     * con una regeneración previa) para no crear duplicados por reintentos o
     * carreras que ya cerró la concurrencia optimista de arriba.
     */
    private suspend fun regenerateNextAssignment(
        householdId: String,
        taskId: String,
        task: TaskResponse,
        completedMemberId: String,
        mandatory: Boolean,
        nextDueDate: Long?,
        existingAssignments: List<TaskAssignmentResponse>? = null
    ) {
        if (nextDueDate == null) return
        val nextMemberId = RecurrenceRules.resolveRotationAssignee(
            task.assignmentRotation, nextDueDate, completedMemberId
        )
        val assignments = existingAssignments ?: try {
            getAssignments(householdId, taskId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val alreadyExists = assignments.any {
            it.memberId == nextMemberId && it.dueDate == nextDueDate && it.status == "assigned"
        }
        if (alreadyExists) return
        assignTask(
            householdId = householdId,
            taskId = taskId,
            memberIds = listOf(nextMemberId),
            mandatory = mandatory,
            dueDate = nextDueDate,
            taskTitle = task.title
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
    ) = taskRepository.updateAssignmentGoogleEventId(householdId, taskId, assignmentId, googleEventId)

    // ────────────────────────────────────────────────────────
    //  Penalty & Recurrence Logic
    // ────────────────────────────────────────────────────────

    /** Resultado de resolver puntos otorgados + puntualidad al completar una tarea. */
    private data class CompletionOutcome(val onTime: Boolean, val pointsAwarded: Int)

    /**
     * Calcula si se completó a tiempo + los puntos a otorgar (con penalización
     * por retraso si toca), a partir de una fecha límite concreta.
     *
     * Compartido por [completeTask] (sin asignación — usa `task.dueDate` para
     * "once", o `task.nextDueAt` ajustado con [RecurrenceRules.endOfDueDay]
     * para recurrentes) y [completeAssignment] (con asignación — usa
     * `assignment.dueDate`, con el mismo ajuste si la tarea es recurrente).
     * Antes solo `completeAssignment` calculaba penalización; `completeTask`
     * otorgaba siempre los puntos íntegros con `onTime=true` fijo, ignorando
     * `task.dueDate`/`penaltyMode` — bug que permitía evitar la penalización
     * completando desde la lista principal en vez del detalle. Y antes de
     * `nextDueAt`, `task.dueDate` valía SIEMPRE 0 para tareas recurrentes
     * (daily/weekly/monthly), así que la penalización nunca se aplicaba ahí
     * tampoco vía `completeAssignment` salvo que la asignación tuviera una
     * `dueDate` explícita.
     *
     * `dueDate == 0` significa "sin fecha límite" (ver `TaskResponse.dueDate`)
     * y nunca penaliza — incluye tareas recurrentes antiguas sin `nextDueAt`
     * todavía (migración aditiva: fallback al comportamiento previo, sin
     * penalización, hasta que la próxima compleción puebla el campo).
     */
    private fun resolveCompletionOutcome(task: TaskResponse, dueDate: Long, now: Long): CompletionOutcome {
        val onTime = dueDate == 0L || now <= dueDate
        val pointsAwarded = if (onTime) {
            task.points
        } else {
            val penalty = calculatePenalty(task, dueDate, now)
            maxOf(task.points - penalty, 0)
        }
        return CompletionOutcome(onTime, pointsAwarded)
    }

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
     * Delega en [RecurrenceRules.nextOccurrence] (testeada en
     * `RecurrenceRulesTest`) en vez de reimplementar el cálculo — antes este
     * método tenía su propia lógica, sin test, y con una diferencia real de
     * comportamiento en "monthly": ignoraba `task.recurrenceDay` (el día fijo
     * configurado por el usuario) y usaba en su lugar el día de la compleción,
     * así que una tarea "día 28 de cada mes" completada el 5 saltaba al 28 del
     * mes SIGUIENTE en vez de al 28 de este mes. `RecurrenceRules.nextOccurrence`
     * sí respeta `recurrenceDay`. Nota: como efecto colateral de unificar, la
     * hora de la fecha límite calculada pasa de las 12:00 a las 00:00 hora
     * local (medianoche, como ya hacía `nextOccurrence` para otros usos en la
     * app) — un cambio menor de cuándo exactamente empieza a contar como
     * "atrasada" una tarea recurrente, aceptado como parte de tener una única
     * fuente de verdad para este cálculo.
     */
    private fun calculateNextDueDate(task: TaskResponse, afterMs: Long): Long? {
        if (task.frequency !in setOf("daily", "weekly", "monthly")) return null
        return RecurrenceRules.nextOccurrence(
            nowEpochMs = afterMs,
            frequency = task.frequency,
            day = task.recurrenceDay,
            weeklyDays = task.recurrenceDays
        )
    }

    /** [FirestoreValue] para el campo `nextDueAt`: entero si no es null, `NULL_VALUE` si lo es. */
    private fun nextDueAtValue(nextDueDate: Long?): FirestoreValue =
        if (nextDueDate != null) FirestoreValue(integerValue = nextDueDate.toString()) else FirestoreValue(nullValue = "NULL_VALUE")

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
        dueDate: Long = 0,
        lastCompletedDate: Long? = null
    ) = taskRepository.updateTask(
        householdId, taskId, title, description, points, frequency, recurrenceDays, recurrenceDay,
        tags, subtasks, penaltyMode, penaltyValue, penaltyInterval, penaltyMax, assignmentRotation, dueDate,
        lastCompletedDate
    )

    /**
     * Update only the subtasks array on a task document.
     * Used for quick toggling of individual subtask checkboxes.
     */
    suspend fun updateSubtasks(
        householdId: String,
        taskId: String,
        subtasks: List<Subtask>
    ) = taskRepository.updateSubtasks(householdId, taskId, subtasks)

    /**
     * Delete a task document.
     */
    suspend fun deleteTask(householdId: String, taskId: String) = taskRepository.deleteTask(householdId, taskId)

    // ────────────────────────────────────────────────────────
    //  Comments (subcollection under households/{id}/tasks/{taskId})
    // ────────────────────────────────────────────────────────

    /** Add a comment to a task. */
    suspend fun addComment(
        householdId: String,
        taskId: String,
        authorName: String,
        text: String
    ): org.taskhub.network.models.CommentResponse = taskRepository.addComment(householdId, taskId, authorName, text)

    /** List comments for a task. */
    suspend fun getComments(
        householdId: String,
        taskId: String
    ): List<org.taskhub.network.models.CommentResponse> = taskRepository.getComments(householdId, taskId)

    // ────────────────────────────────────────────────────────
    //  Messages (subcollection under households/{id})
    // ────────────────────────────────────────────────────────

    /** Send a chat message to a household. */
    suspend fun sendMessage(
        householdId: String,
        memberId: String,
        authorName: String,
        text: String
    ): org.taskhub.network.models.MessageResponse = householdRepository.sendMessage(householdId, memberId, authorName, text)

    /** List chat messages for a household, oldest first. */
    suspend fun getMessages(householdId: String): List<org.taskhub.network.models.MessageResponse> =
        householdRepository.getMessages(householdId)

    // ────────────────────────────────────────────────────────
    //  Notifications — delegado en NotificationRepository (fase 2.1 del
    //  refactor). Facade temporal: firma pública idéntica, sin lógica propia.
    // ────────────────────────────────────────────────────────

    suspend fun createNotification(
        householdId: String,
        memberId: String,
        taskId: String,
        title: String,
        message: String
    ): NotificationResponse = notificationRepository.createNotification(householdId, memberId, taskId, title, message)

    suspend fun getNotifications(householdId: String): List<NotificationResponse> =
        notificationRepository.getNotifications(householdId)

    suspend fun markNotificationRead(householdId: String, notificationId: String) =
        notificationRepository.markNotificationRead(householdId, notificationId)

    // ────────────────────────────────────────────────────────
    //  Rewards — delegado en RewardsRepository (fase 2.2 del refactor),
    //  salvo redeemReward (capa de puntos, se queda hasta la fase de Member).
    // ────────────────────────────────────────────────────────

    suspend fun getRewards(householdId: String): List<RewardResponse> = rewardsRepository.getRewards(householdId)

    suspend fun createReward(
        householdId: String,
        title: String,
        description: String,
        cost: Int,
        icon: String,
        createdBy: String
    ): RewardResponse = rewardsRepository.createReward(householdId, title, description, cost, icon, createdBy)

    suspend fun deleteReward(householdId: String, rewardId: String) =
        rewardsRepository.deleteReward(householdId, rewardId)

    /** Redeem a reward: subtract points from member, record redemption. Requires auth (write). */
    suspend fun redeemReward(
        householdId: String,
        rewardId: String,
        memberId: String,
        pointsSpent: Int
    ): RewardRedemption {
        val now = Clock.System.now().toEpochMilliseconds()

        // Validar saldo contra una lectura fresca del miembro — a diferencia de
        // donatePoints (que sí valida vía PointsRules), esta función descontaba
        // puntos sin comprobar el saldo en ningún punto del repositorio,
        // confiando solo en el `canAfford` (potencialmente obsoleto) de la UI.
        // No elimina la carrera entre dos canjes concurrentes (ver
        // docs/atomicidad-commit-pendiente.md), pero evita el caso más común:
        // un único canje con saldo insuficiente por datos ya desincronizados.
        val member = getMembers(householdId).find { it.id == memberId }
            ?: throw IllegalStateException("Miembro no encontrado")
        if (member.totalPoints < pointsSpent) {
            throw IllegalStateException("Saldo insuficiente para canjear esta recompensa")
        }

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
    suspend fun getRewardRedemptions(householdId: String): List<RewardRedemption> =
        rewardsRepository.getRewardRedemptions(householdId)

    // ────────────────────────────────────────────────────────
    //  Request helpers
    // ────────────────────────────────────────────────────────

    /** Ver [FirestoreClient.tryAuthOrApiKey] — delegado tal cual, mismo motivo que [withAuth]. */
    private suspend fun HttpRequestBuilder.tryAuthOrApiKey() = with(firestoreClient) { tryAuthOrApiKey() }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    /** Ver [FirestoreClient.extractDocId] — delegado tal cual, mismo motivo que [withAuth]. */
    private fun extractDocId(resourceName: String, operation: String): String =
        firestoreClient.extractDocId(resourceName, operation)

    private fun toMemberResponse(
        doc: FirestoreDocumentResponse,
        householdId: String,
        operation: String = "getMembers"
    ): MemberResponse = FirestoreParsers.toMemberResponse(doc, householdId, operation)

    companion object {
        /** Firebase Web API Key for task-hub-62f98 (Firebase Console → Project Settings → General). */
        const val DEFAULT_API_KEY = "AIzaSyD5Xo11SqvysWRgEFv_91rBjYuFIq93lV8"
    }
}
