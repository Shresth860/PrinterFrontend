package com.bizbite.printer.data.remote

import com.bizbite.printer.data.security.TokenStore
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TokenAuthenticatorTest {
    private val backendBaseUrl = "https://printer-backend-2.onrender.com/"

    @Test
    fun refreshUsesBackendContractAndStoresExactExpiration() {
        val expectedExpiry = 1_893_553_445_000L
        val tokenStore = mockk<TokenStore>()
        every { tokenStore.accessToken() } returns "expired-access"
        every { tokenStore.refreshToken() } returns "refresh-value"
        every { tokenStore.clear() } just runs
        every { tokenStore.save(any(), any(), any()) } just runs

        var refreshPath: String? = null
        var refreshBody: String? = null
        val refreshClient = OkHttpClient.Builder().addInterceptor { chain ->
            refreshPath = chain.request().url.encodedPath
            val body = Buffer()
            chain.request().body?.writeTo(body)
            refreshBody = body.readUtf8()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """{"accessToken":"new-access","refreshToken":"new-refresh","deviceId":"device-1","sellerId":"seller-1","accessTokenExpiresAt":"2030-01-02T03:04:05Z"}"""
                        .toResponseBody("application/json".toMediaType())
                )
                .build()
        }.build()
        val authenticator = TokenAuthenticator(
            tokenStore,
            Json { ignoreUnknownKeys = true },
            backendBaseUrl,
            refreshClient
        )
        val originalRequest = Request.Builder()
            .url("${backendBaseUrl}api/print-jobs/pending?printerId=printer-1")
            .header("Authorization", "Bearer expired-access")
            .build()
        val unauthorizedResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        val retry = authenticator.authenticate(null, unauthorizedResponse)

        assertNotNull(retry)
        assertEquals("/api/v1/auth/refresh", refreshPath)
        assertEquals("""{"refreshToken":"refresh-value"}""", refreshBody)
        assertEquals("Bearer new-access", retry?.header("Authorization"))
        verify(exactly = 1) { tokenStore.save("new-access", "new-refresh", expectedExpiry) }
    }

    @Test
    fun transientRefreshServerFailureDoesNotClearStoredCredentials() {
        val tokenStore = mockk<TokenStore>()
        every { tokenStore.accessToken() } returns "expired-access"
        every { tokenStore.refreshToken() } returns "refresh-value"
        every { tokenStore.isAccessTokenExpired() } returns true
        every { tokenStore.clear() } just runs
        val refreshClient = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body("unavailable".toResponseBody())
                .build()
        }.build()
        val authenticator = TokenAuthenticator(
            tokenStore,
            Json { ignoreUnknownKeys = true },
            backendBaseUrl,
            refreshClient
        )
        val originalRequest = Request.Builder()
            .url("${backendBaseUrl}api/print-jobs/pending?printerId=printer-1")
            .header("Authorization", "Bearer expired-access")
            .build()
        val unauthorizedResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        val retry = authenticator.authenticate(null, unauthorizedResponse)

        assertEquals(null, retry)
        verify(exactly = 0) { tokenStore.clear() }
    }
}
