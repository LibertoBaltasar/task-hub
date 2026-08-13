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
                if (token != null) {
                    handleGoogleToken(token)
                    GoogleSignInResultHolder.reset()
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

    /** Intercambia el idToken de Google por credenciales de Firebase y restaura datos. */
    private suspend fun handleGoogleToken(googleIdToken: String) {
        try {
            val result = repo.signInWithGoogle(googleIdToken)
            settingsStore.setGoogleAuth(result.uid, result.email)
            restoreHouseholds(result.uid)
            syncHouseholdsToCloud()
            _state.value = GoogleAuthState.SignedIn(result.email)
        } catch (e: Exception) {
            _state.value = GoogleAuthState.Error(
                e.message ?: "Error al iniciar sesión con Google"
            )
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
     * Sincroniza los hogares compartidos del usuario con Firestore (users/{uid}).
     * Solo se sincroniza si hay sesión de Google iniciada. Ignora el espacio
     * Personal (es por-dispositivo y se recrea solo).
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
