package com.vamp.haron.data.cloud

import android.util.Base64
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PKCE OAuth2 helper for cloud providers.
 * Handles code verifier/challenge generation and token exchange.
 */
object CloudOAuthHelper {

    /** Google uses loopback redirect (Desktop client type) — caught by HttpFileServer */
    var REDIRECT_URI_GDRIVE = "http://127.0.0.1:8080/oauth/callback"
        private set

    /** Update Google redirect URI with actual server port */
    fun setGdriveLoopbackPort(port: Int) {
        REDIRECT_URI_GDRIVE = "http://127.0.0.1:$port/oauth/callback"
    }
    const val REDIRECT_URI_DROPBOX = "haron://oauth/dropbox"
    const val REDIRECT_URI_ONEDRIVE = "haron://oauth/onedrive"

    /** Pending OAuth result from deep link redirect */
    data class PendingAuth(val providerScheme: String, val code: String)
    val pendingAuth = MutableStateFlow<PendingAuth?>(null)

    // In-flight PKCE state per provider
    private val codeVerifiers = mutableMapOf<String, String>()

    fun generateCodeVerifier(providerScheme: String): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        codeVerifiers[providerScheme] = verifier
        return verifier
    }

    fun getCodeVerifier(providerScheme: String): String? = codeVerifiers[providerScheme]

    fun clearCodeVerifier(providerScheme: String) {
        codeVerifiers.remove(providerScheme)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresIn: Long = 0
    )

    suspend fun exchangeCodeForToken(
        tokenUrl: String,
        params: Map<String, String>
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                EcosystemLogger.d(HaronConstants.TAG, "CloudOAuth: token exchange attempt $attempt/$maxRetries")
                val url = URL(tokenUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000

                val body = params.entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }

                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                } else {
                    val error = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                    EcosystemLogger.e(HaronConstants.TAG, "CloudOAuth: token exchange failed: $responseCode, $error")
                    return@withContext Result.failure(Exception("Token exchange failed: $responseCode"))
                }

                val json = JSONObject(responseBody)
                EcosystemLogger.d(HaronConstants.TAG, "CloudOAuth: token exchange success")
                return@withContext Result.success(
                    TokenResponse(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.optString("refresh_token", null),
                        expiresIn = json.optLong("expires_in", 0)
                    )
                )
            } catch (e: Exception) {
                lastError = e
                EcosystemLogger.e(HaronConstants.TAG, "CloudOAuth: token exchange error (attempt $attempt): ${e.message}")
                if (attempt < maxRetries) {
                    delay(attempt * 1500L) // 1.5s, 3s
                }
            }
        }

        Result.failure(lastError ?: Exception("Token exchange failed after $maxRetries attempts"))
    }
}
