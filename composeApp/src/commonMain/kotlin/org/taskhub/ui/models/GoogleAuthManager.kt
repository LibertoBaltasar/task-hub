package org.taskhub.ui.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.platform.GoogleSignInResultHolder
import org.taskhub.platform.getGoogleCalendarAccessToken
import org.taskhub.platform.launchGoogleSignIn
import org.taskhub.platform.revokeGoogleCalendarAccess
import org.taskhub.storage.HouseholdStore
import org.taskhub.storage.SettingsStore

/**
 * Estado del login del usuario.
 */
sealed class GoogleAuthState {
    data object Idle : GoogleAuthState()
    data object SigningIn : GoogleAuthState()
    data class SignedIn(val email: String?) : GoogleAuthState()
    data object Anonymous : GoogleAuthState()
    data class Error(val message: String) : GoogleAuthState()
}

/**
 * Señala que [GoogleAuthManager.deleteAccount] no pudo completar el
 * cascade-delete de TODOS los hogares del usuario (algún `leaveHousehold`/
 * `deleteHousehold` falló) — por eso la cuenta de Firebase Auth (paso
 * irreversible) NO se ha borrado, para que el usuario conserve la sesión con
 * la que reintentarlo (panel v4, Experto 12 hallazgo #2).
 */
class AccountDeletionCascadeException :
    Exception("No se pudieron borrar/abandonar todos los hogares del usuario; la cuenta no se ha eliminado.")

/**
 * Orquesta el login con Google (y el fallback anónimo).
 *
 * - [signIn] lanza el flujo nativo de Google Sign-In y, al recibir el idToken,
 *   lo intercambia por credenciales de Firebase Auth vía [FirestoreRepository.signInWithGoogle].
 * - Guarda el UID estable de Google en [SettingsStore] para que los datos
 *   sobrevivan a una reinstalación.
 * - Restaura los hogares del usuario desde Firestore (users/{uid}).
 *
 * Se registra como singleton en Koin para que HomeScreen y Ajustes compartan estado.
 */
class GoogleAuthManager(
    private val repo: FirestoreRepository,
    private val settingsStore: SettingsStore,
    private val householdStore: HouseholdStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<GoogleAuthState>(
        if (settingsStore.isGoogleLoggedIn()) {
            GoogleAuthState.SignedIn(settingsStore.getGoogleEmail())
        } else {
            GoogleAuthState.Anonymous
        }
    )
    val state: StateFlow<GoogleAuthState> = _state.asStateFlow()

    init {
        // Observa el resultado del flujo nativo de Google Sign-In
        scope.launch {
            GoogleSignInResultHolder.result.collect { token ->
                when {
                    // null = en curso (aún no resuelto); no hacer nada.
                    token == null -> Unit
                    // "" = cancelado / sin token → volver a Anonymous para no
                    // quedarse colgado en "Conectando con Google...".
                    token.isEmpty() -> {
                        GoogleSignInResultHolder.reset()
                        _state.value = GoogleAuthState.Anonymous
                    }
                    else -> {
                        handleGoogleToken(token)
                        GoogleSignInResultHolder.reset()
                    }
                }
            }
        }
    }

    /**
     * Lanza el flujo de Google Sign-In (abre el selector de cuenta).
     *
     * Guarda de reentrancia: si ya hay un flujo en curso (doble-tap en
     * "Vincular con Google", o [linkCalendar] invocado dos veces casi a la vez
     * desde Ajustes y desde el detalle de una tarea), una segunda llamada NO
     * relanza el flujo nativo ni resetea [GoogleSignInResultHolder] — eso
     * dejaría el resultado del primer flujo (que puede llegar después del
     * reset) sin nadie que lo recoja, colgando ese `state.first { ... }` en
     * [linkCalendar] para siempre.
     */
    fun signIn() {
        if (_state.value is GoogleAuthState.SigningIn) return
        _state.value = GoogleAuthState.SigningIn
        GoogleSignInResultHolder.reset()
        launchGoogleSignIn()
    }

    /**
     * Cierra sesión: vuelve al modo anónimo.
     *
     * Limpia también el `fcmToken` del perfil global de la cuenta que cierra
     * sesión (best-effort, en segundo plano) — sin esto, en un dispositivo
     * familiar compartido el token de push quedaba asociado indefinidamente
     * a la cuenta anterior tras cerrar sesión localmente (panel de revisión
     * 2026-09-03/04, Experto 10, NUEVO).
     */
    fun signOut() {
        val uidBeingSignedOut = settingsStore.getGoogleUid()
        settingsStore.clearGoogleAuth()
        _state.value = GoogleAuthState.Anonymous
        if (uidBeingSignedOut != null) {
            scope.launch {
                try {
                    repo.clearFcmToken(uidBeingSignedOut)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // No crítico: el token se sobrescribirá en el próximo login de esa cuenta.
                }
            }
        }
    }

    /**
     * Elimina la cuenta del usuario actual (Google o anónima) y TODOS sus
     * datos — ver hallazgo de privacidad "sin flujo de eliminar cuenta"
     * (docs/review-panel-expertos-v3-2026-09-01.md, Experto 10 #3).
     *
     * Orden:
     *  1. Su espacio Personal (isPersonal=true) → borrado completo en cascada
     *     ([FirestoreRepository.deleteHousehold]), es solo suyo.
     *  2. Cualquier otro hogar donde sea miembro → se le da de baja igual que
     *     si lo abandonara ([FirestoreRepository.leaveHousehold]): borra SU
     *     propio miembro (y el hogar entero si quedaba vacío), pero conserva
     *     el contenido compartido de otros miembros — borrar por completo un
     *     hogar familiar entero porque el dueño elimina su cuenta destruiría
     *     datos que no son (solo) suyos.
     *
     *     Si CUALQUIER hogar de este bucle falla (offline, cascade parcial —
     *     ver [org.taskhub.network.HouseholdCascadeIncompleteException] —
     *     etc.), el fallo se ACUMULA y el resto de pasos (perfil global,
     *     borrado de la cuenta Auth) NO se ejecutan: se devuelve
     *     [AccountDeletionCascadeException] para que el usuario conserve la
     *     sesión con la que reintentarlo, en vez de perder la cuenta Auth
     *     (paso irreversible) con hogares a medio borrar sin ninguna forma
     *     de completarlo (panel v4, Experto 12 hallazgo #2).
     *  3. Su perfil global (`users/{uid}`) — best-effort, no bloquea el resto.
     *  4. Revoca el consentimiento OAuth de Google Calendar (best-effort, ver
     *     [revokeGoogleCalendarAccess]) — panel v4, Experto 10 hallazgo #5.
     *  5. La cuenta de Firebase Auth en sí — el paso irreversible final; si
     *     este falla SÍ se reporta como error (la cuenta sigue activa).
     * Termina limpiando todo el estado local (tokens, hogares guardados).
     */
    suspend fun deleteAccount(): Result<Unit> {
        val myId = currentUserId()
        val households = householdStore.getSavedHouseholds()
        var hadCascadeFailure = false
        for (h in households) {
            try {
                if (h.isPersonal) {
                    repo.deleteHousehold(h.id)
                } else {
                    repo.leaveHousehold(h.id, myId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                hadCascadeFailure = true
            }
        }
        if (hadCascadeFailure) {
            return Result.failure(AccountDeletionCascadeException())
        }
        if (myId != null) {
            try {
                repo.deleteUserProfile(myId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No crítico: el perfil global huérfano no es un dato con
                // identidad reclamable sin la cuenta que acabamos de borrar.
            }
        }
        return try {
            repo.deleteFirebaseAccount()
            if (settingsStore.hasGoogleLinked()) {
                try {
                    revokeGoogleCalendarAccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // No crítico: el access token en sí caduca solo en ~1h.
                }
            }
            householdStore.clearAll()
            settingsStore.unlinkGoogleCalendar()
            settingsStore.clearGoogleAuth()
            settingsStore.clearAnonymousAuth()
            _state.value = GoogleAuthState.Anonymous
            // Recrea el espacio Personal para la (nueva) identidad anónima —
            // mismo bootstrap que hace App.kt en cada arranque en frío. Sin
            // esto, HomeScreen se quedaría sin ningún hogar que mostrar hasta
            // que el usuario reiniciara la app entera.
            try {
                val personal = repo.getOrCreatePersonalHousehold()
                householdStore.replacePersonalHousehold(personal.id)
                repo.ensurePersonalMember(personal.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline/transitorio: App.kt lo reintentará en el próximo
                // arranque real de la app.
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fuerza una reautenticación reciente con Google justo antes de eliminar
     * la cuenta (acción irreversible) — sin esto, una sesión de Firebase ya
     * iniciada (posiblemente horas o días atrás) bastaba para borrar la
     * cuenta desde un dispositivo desatendido/robado (panel v4, Experto 9).
     * Solo aplica a cuentas vinculadas a Google: una cuenta anónima no tiene
     * ningún proveedor de identidad externo contra el que reautenticar (el
     * único "factor" es tener el dispositivo desbloqueado, ya verificado
     * antes de llegar a Ajustes), así que no hay nada más que hacer y
     * devuelve true directamente.
     *
     * Reutiliza [signIn] (mismo patrón que [linkCalendar]) para no duplicar
     * el manejo de [GoogleSignInResultHolder]/reentrancia: si el usuario
     * cancela el selector de cuenta, el estado vuelve a [GoogleAuthState.Anonymous]
     * (comportamiento ya existente y aceptado en [linkCalendar] ante una
     * cancelación) y esta función devuelve false.
     */
    suspend fun reauthenticateForDeletion(): Boolean {
        if (_state.value !is GoogleAuthState.SignedIn) return true
        signIn()
        val result = state.first {
            it is GoogleAuthState.SignedIn || it is GoogleAuthState.Anonymous || it is GoogleAuthState.Error
        }
        return result is GoogleAuthState.SignedIn
    }

    /**
     * Obtiene bajo demanda un access token OAuth con scope de Calendar (efímero,
     * ~1h) y actualiza el flag "vinculado" de [SettingsStore] según el resultado:
     * éxito → vinculado; fallo (sin cuenta, consentimiento denegado, revocado) →
     * desvinculado. No se persiste como si fuera de larga duración — se vuelve a
     * pedir cada vez que hace falta, y la capa de plataforma cachea/refresca la
     * cuenta subyacente de forma transparente.
     */
    suspend fun ensureCalendarAccessToken(): String? {
        val token = getGoogleCalendarAccessToken()
        settingsStore.setGoogleAccessToken(token)
        return token
    }

    /**
     * Flujo completo de "vincular cuenta de Google Calendar" desde ajustes o
     * desde el detalle de una tarea: si no hay sesión de Google, la inicia
     * primero (abre el selector de cuenta y espera el resultado) y, una vez
     * autenticado, pide el token de Calendar. Devuelve true si quedó vinculado.
     */
    suspend fun linkCalendar(): Boolean {
        if (_state.value !is GoogleAuthState.SignedIn) {
            signIn()
            val result = state.first {
                it is GoogleAuthState.SignedIn || it is GoogleAuthState.Anonymous || it is GoogleAuthState.Error
            }
            if (result !is GoogleAuthState.SignedIn) return false
        }
        return ensureCalendarAccessToken() != null
    }

    /**
     * ID estable del usuario actual: el UID de Google si hay sesión iniciada,
     * o el localId anónimo en caso contrario. Sirve para identificar los
     * miembros creados por este usuario en un hogar (member.userId).
     */
    fun currentUserId(): String? = settingsStore.getGoogleUid() ?: repo.getLocalId()

    /** Intercambia el idToken de Google por credenciales de Firebase y restaura datos. */
    private suspend fun handleGoogleToken(googleIdToken: String) {
        try {
            val result = repo.signInWithGoogle(googleIdToken)
            settingsStore.setGoogleAuth(result.uid, result.email)
            restoreHouseholds(result.uid)
            repointPersonalHousehold()
            syncHouseholdsToCloud()
            syncGoogleAvatar(result)
            _state.value = GoogleAuthState.SignedIn(result.email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = GoogleAuthState.Error(
                e.message ?: "Error al iniciar sesión con Google"
            )
        }
    }

    /**
     * Guarda la foto de perfil de Google como avatarUrl del perfil global, solo
     * si el usuario todavía no tiene una foto propia (para no pisar una subida
     * a mano en EditProfileScreen con la foto de Google en cada login).
     */
    private suspend fun syncGoogleAvatar(result: FirestoreRepository.GoogleSignInResult) {
        val photoUrl = result.photoUrl ?: return
        try {
            val existing = repo.getUserProfile(result.uid)
            if (!existing?.avatarUrl.isNullOrBlank()) return
            repo.upsertUserProfile(
                userId = result.uid,
                displayName = existing?.displayName?.ifBlank { result.displayName ?: "" }
                    ?: (result.displayName ?: ""),
                avatarUrl = photoUrl,
                avatarEmoji = existing?.avatarEmoji ?: "",
                bio = existing?.bio ?: "",
                status = existing?.status ?: ""
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No crítico: se reintenta en el próximo login.
        }
    }

    /** Restaura los hogares vinculados a la cuenta de Google desde Firestore. */
    private suspend fun restoreHouseholds(uid: String) {
        val ids = repo.loadUserHouseholds(uid)
        for (id in ids) {
            try {
                val household = repo.getHousehold(id)
                householdStore.saveHousehold(
                    householdId = id,
                    householdName = household.name,
                    inviteCode = household.inviteCode,
                    isPersonal = household.isPersonal
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Hogar obsoleto/eliminado — ignorar
            }
        }
    }

    /**
     * Restaura los hogares compartidos desde la nube (users/{uid}) y re-apunta el
     * espacio Personal. Debe llamarse al arrancar la app si hay sesión de Google
     * persistida, para que los hogares creados/unidos en OTRO dispositivo con la
     * misma cuenta aparezcan sin re-loguearse (antes `restoreHouseholds` solo se
     * ejecutaba en el login explícito).
     *
     * Es aditivo (unión) y tolerante a fallos: no lanza excepciones. Es `suspend`
     * para que App.kt pueda esperarla ANTES de mostrar HomeScreen (que lee la
     * lista de hogares una sola vez y no reacciona a cambios).
     */
    suspend fun restoreFromCloudOnStartup() {
        val uid = settingsStore.getGoogleUid() ?: return
        try {
            restoreHouseholds(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No crítico: se reintenta en el próximo arranque/login.
        }
        try {
            repointPersonalHousehold()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No crítico.
        }
    }

    /**
     * Re-apunta el espacio Personal a la identidad de Google para que sea
     * interdispositivo: con el UID estable de Google, todos los dispositivos
     * resuelven el mismo documento `personal_{uid}` (vía
     * [FirestoreRepository.getOrCreatePersonalHousehold]). Si el usuario venía
     * del modo anónimo, su espacio Personal por-dispositivo se sustituye por el
     * compartido; las tareas del anónimo quedan en el hogar antiguo (no migran).
     */
    private suspend fun repointPersonalHousehold() {
        try {
            val personal = repo.getOrCreatePersonalHousehold()
            householdStore.replacePersonalHousehold(personal.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline/transitorio: App.kt lo reintenta en el próximo arranque.
        }
    }

    /**
     * Sincroniza los hogares compartidos del usuario con Firestore (users/{uid}).
     * Solo se sincroniza si hay sesión de Google iniciada. Ignora el espacio
     * Personal: no se guarda aquí, se resuelve de forma determinista
     * (personal_{uid}) desde [FirestoreRepository.getOrCreatePersonalHousehold].
     */
    fun syncHouseholdsToCloud() {
        val uid = settingsStore.getGoogleUid() ?: return
        val ids = householdStore.getSavedHouseholds()
            .filter { !it.isPersonal }
            .map { it.id }
        scope.launch {
            try {
                repo.saveUserHouseholds(uid, ids)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No crítico — se reintenta en el próximo cambio
            }
        }
    }

    /**
     * Cancela el `CoroutineScope` interno (observador de
     * [GoogleSignInResultHolder] + corrutinas de [syncHouseholdsToCloud] en
     * vuelo). No implementa `java.io.Closeable` porque no existe en
     * `commonMain` de Kotlin Multiplatform (es JVM-only) — introducirlo
     * forzaría una dependencia no disponible en iOS. En su lugar, Koin
     * invoca este método vía `onClose` al cerrar el contenedor de DI (ver
     * `AppModule.kt`). Impacto práctico hoy: nulo — la app nunca llama a
     * `koin.close()` (este manager vive tanto como el proceso), pero deja
     * el manager correctamente cerrable si eso cambia en el futuro (p.ej.
     * tests instrumentados que recrean el contenedor de Koin entre casos).
     */
    fun close() {
        scope.cancel()
    }
}
