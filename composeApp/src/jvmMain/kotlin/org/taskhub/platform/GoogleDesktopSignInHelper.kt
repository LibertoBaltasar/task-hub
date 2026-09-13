/**
 * Flujo OAuth 2.0 authorization-code + PKCE con redirect loopback
 * (127.0.0.1) para obtener un id_token de Google en JVM/desktop, donde no
 * existe un SDK nativo de Google Sign-In (a diferencia de Android, ver
 * `GoogleSignInHelper` en androidMain). Lo dispara [launchGoogleSignIn]
 * (Platform.jvm.kt); el id_token resultante se entrega a
 * [GoogleSignInResultHolder] EXACTAMENTE igual que Android — de ahí en
 * adelante lo consume el mismo código de commonMain
 * ([org.taskhub.ui.models.GoogleAuthManager]), que lo intercambia por
 * credenciales de Firebase vía `accounts:signInWithIdp`.
 */
package org.taskhub.platform

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Requiere [GoogleOAuthConfig.CLIENT_ID]/[GoogleOAuthConfig.CLIENT_SECRET]
 * rellenos (ver su KDoc) — sin ellos, [signIn] lanza [IllegalStateException]
 * inmediatamente, sin abrir navegador ni socket.
 */
object GoogleDesktopSignInHelper {
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    /** Tiempo máximo esperando a que el usuario complete el consentimiento en el navegador. */
    private const val CALLBACK_TIMEOUT_MILLIS = 5 * 60 * 1000

    private val secureRandom = SecureRandom()

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Ejecuta el flujo completo (navegador + callback local + intercambio de
     * código por token) y devuelve el id_token de Google, o `null` si el
     * usuario cancela, expira el timeout o falla cualquier paso — nunca
     * lanza salvo config vacía (ver KDoc de la clase), para que el llamante
     * (`launchGoogleSignIn`) solo tenga que distinguir "configuración
     * inválida" (excepción) de "sin token" (`null`).
     */
    suspend fun signIn(): String? {
        val clientId = GoogleOAuthConfig.CLIENT_ID
        val clientSecret = GoogleOAuthConfig.CLIENT_SECRET
        check(clientId.isNotBlank() && clientSecret.isNotBlank()) {
            "Google Sign-In en desktop no está configurado: rellena " +
                "GoogleOAuthConfig.CLIENT_ID/CLIENT_SECRET (ver su KDoc)."
        }

        val state = randomUrlSafeString(16)
        val codeVerifier = randomUrlSafeString(32)
        val codeChallenge = codeChallengeFor(codeVerifier)

        val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        return try {
            val redirectUri = "http://127.0.0.1:${serverSocket.localPort}/callback"
            openBrowser(buildAuthorizationUrl(clientId, redirectUri, state, codeChallenge))

            val code = awaitCallback(serverSocket, state) ?: return null
            exchangeCodeForIdToken(code, clientId, clientSecret, redirectUri, codeVerifier)
        } finally {
            runCatching { serverSocket.close() }
        }
    }

    private fun randomUrlSafeString(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** PKCE `code_challenge` S256: base64url(SHA-256(code_verifier)) — RFC 7636. */
    private fun codeChallengeFor(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String
    ): String {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        return "$AUTH_ENDPOINT?client_id=${enc(clientId)}" +
            "&redirect_uri=${enc(redirectUri)}" +
            "&response_type=code" +
            "&scope=${enc("openid email profile")}" +
            "&code_challenge=${enc(codeChallenge)}" +
            "&code_challenge_method=S256" +
            "&state=${enc(state)}"
    }

    private fun openBrowser(url: String) {
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "No se pudo abrir el navegador del sistema para iniciar sesión con Google."
        }
        Desktop.getDesktop().browse(URI(url))
    }

    /**
     * Espera la única conexión de callback en [serverSocket] (el navegador
     * redirigido tras el consentimiento), valida `state` y devuelve el
     * `code` recibido. Devuelve `null` si expira el timeout, Google reporta
     * un error (p.ej. `error=access_denied` al cancelar el usuario), o
     * `state` no coincide (posible CSRF — se descarta la respuesta sin
     * canjear ningún código).
     */
    private suspend fun awaitCallback(serverSocket: ServerSocket, expectedState: String): String? =
        withContext(Dispatchers.IO) {
            serverSocket.soTimeout = CALLBACK_TIMEOUT_MILLIS
            val socket = try {
                serverSocket.accept()
            } catch (e: SocketTimeoutException) {
                return@withContext null
            }
            socket.use {
                val params = readCallbackParams(it) ?: emptyMap()
                val ok = params["error"] == null &&
                    params["state"] != null &&
                    params["state"] == expectedState &&
                    !params["code"].isNullOrBlank()
                writeCallbackResponse(it, ok)
                if (ok) params["code"] else null
            }
        }

    /** Parsea la query string de la línea de petición GET (`/callback?code=...&state=...`). */
    private fun readCallbackParams(socket: Socket): Map<String, String>? {
        val requestLine = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine() ?: return null
        val path = requestLine.split(" ").getOrNull(1) ?: return null
        val query = path.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        return query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            pair.substring(0, idx) to URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    /** Página de cierre servida al navegador tras el callback — éxito o error. */
    private fun writeCallbackResponse(socket: Socket, success: Boolean) {
        val message = if (success) {
            "Sesión iniciada. Ya puedes cerrar esta pestaña y volver a Task Hub."
        } else {
            "No se pudo completar el inicio de sesión. Ya puedes cerrar esta pestaña."
        }
        val html = "<html><body><p>$message</p></body></html>"
        val bodyBytes = html.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n" +
            html
        socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
    }

    @Serializable
    private data class TokenResponse(
        val id_token: String? = null,
        val error: String? = null,
        val error_description: String? = null
    )

    /** POST a [TOKEN_ENDPOINT] (Google) — canjea el `code` por tokens usando PKCE, sin necesitar navegador. */
    private suspend fun exchangeCodeForIdToken(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        codeVerifier: String
    ): String? {
        val response: TokenResponse = client.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("code", code)
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
                append("code_verifier", codeVerifier)
            }
        ).body()
        return response.id_token?.takeIf { it.isNotBlank() }
    }
}
