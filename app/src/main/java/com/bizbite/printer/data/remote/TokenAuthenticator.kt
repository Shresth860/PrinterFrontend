package com.bizbite.printer.data.remote

import com.bizbite.printer.data.security.TokenStore
import java.io.IOException
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val json: Json,
    private val baseUrl: String,
    private val client: OkHttpClient
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            tokenStore.clearAfterAuthFailure()
            return null
        }
        synchronized(refreshLock) {
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val current = tokenStore.accessToken()
            if (!current.isNullOrBlank() && current != failedToken && !tokenStore.isAccessTokenExpired()) {
                return response.request.newBuilder().header("Authorization", "Bearer $current").build()
            }
            val refresh = tokenStore.refreshToken() ?: run {
                tokenStore.clearAfterAuthFailure()
                return null
            }
            val body = json.encodeToString(RefreshRequest(refresh))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val refreshRequest = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/v1/auth/refresh")
                .post(body)
                .build()
            val result = try {
                client.newCall(refreshRequest).execute().use { refreshResponse ->
                    if (!refreshResponse.isSuccessful) {
                        if (refreshResponse.code == 401 || refreshResponse.code == 403) {
                            tokenStore.clearAfterAuthFailure()
                        }
                        null
                    } else {
                        refreshResponse.body?.string()?.let {
                            try {
                                json.decodeFromString<TokenResponse>(it)
                            } catch (_: SerializationException) {
                                null
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                return null
            }
            if (result == null) return null
            val expiration = try {
                result.accessTokenExpiresAtEpochMillis()
            } catch (_: DateTimeParseException) {
                return null
            }
            tokenStore.save(
                result.accessToken,
                result.refreshToken,
                expiration
            )
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${result.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
