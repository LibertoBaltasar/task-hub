package org.taskhub.ui.models

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.taskhub.network.FirestoreRepository
import org.taskhub.platform.GoogleSignInResultHolder
import org.taskhub.platform.getGoogleCalendarAccessToken
import org.taskhub.platform.launchGoogleSignIn
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

    /** Lanza el flujo de Google Sign-In (abre el selector de cuenta). */
    fun signIn() {
        _state.value = GoogleAuthState.SigningIn
        GoogleSignInResultHolder.reset()
        launchGoogleSignIn()
    }

    /** Cierra sesión: vuelve al modo anónimo. */
    fun signOut() {
        settingsStore.clearGoogleAuth()
        _state.value = GoogleAuthState.Anonymous
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
        } catch (_: Exception) {
            // No crítico: se reintenta en el próximo arranque/login.
        }
        try {
            repointPersonalHousehold()
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
            } catch (_: Exception) {
                // No crítico — se reintenta en el próximo cambio
            }
        }
    }
}
