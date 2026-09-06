/**
 * Puente commonMain para recibir el resultado del flujo de Google Sign-In
 * disparado por [launchGoogleSignIn] (expect/actual): cada plataforma
 * publica el resultado aquí en vez de devolverlo directamente, porque el
 * inicio de sesión nativo es asíncrono y basado en callbacks/Activity result.
 */
package org.taskhub.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contenedor multiplataforma del resultado de Google Sign-In.
 *
 * Tras completarse [launchGoogleSignIn] (Android) o no-opear (otras
 * plataformas), el resultado se entrega aquí para que el código de
 * commonMain (p.ej. GoogleAuthManager) pueda observarlo vía [result].
 */
object GoogleSignInResultHolder {
    /** Resultado actual: null = en curso/no iniciado, "" = no-op, token = éxito. */
    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    /** Publica el resultado del intento de sign-in (token, "" si no-op, o null para limpiar). */
    fun setResult(token: String?) {
        _result.value = token
    }

    /** Vuelve al estado "en curso/no iniciado", para lanzar un nuevo intento desde cero. */
    fun reset() {
        _result.value = null
    }
}