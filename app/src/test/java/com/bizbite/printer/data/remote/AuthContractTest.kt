package com.bizbite.printer.data.remote

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.http.POST
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun appLoginUsesRelativePathAndSnakeCaseRequestBody() {
        val endpoint = BbnApi::class.java.methods.single { it.name == "loginApp" }
            .getAnnotation(POST::class.java)
        assertEquals("users/login/app", endpoint.value)
        assertEquals(
            """{"identifier":"9876543210","pin":"2468","fcm_token":"fcm-value"}""",
            json.encodeToString(LoginAppRequest("9876543210", "2468", "fcm-value"))
        )
        assertEquals(
            """{"identifier":"9876543210","pin":"2468"}""",
            json.encodeToString(LoginAppRequest("9876543210", "2468"))
        )
    }

    @Test
    fun appLoginResponseIgnoresUnknownFields() {
        val response = json.decodeFromString<LoginResponse>(
            """{"success":true,"token":"session","isAgreed":true,"user":{"id":"seller","role":"Seller"},"profile":{},"extra":"ignored"}"""
        )
        assertEquals("session", response.token)
        assertEquals("Seller", response.user?.role)
    }

    @Test
    fun pairRequestUsesBackendCamelCaseFields() {
        val request = PairRequest(
            code = "ABCD1234",
            deviceName = "Counter",
            androidVersion = "14",
            appVersion = "1.0.0"
        )

        assertEquals(
            """{"code":"ABCD1234","deviceName":"Counter","androidVersion":"14","appVersion":"1.0.0"}""",
            json.encodeToString(request)
        )
    }

    @Test
    fun refreshRequestAndTokenResponseUseBackendCamelCaseFields() {
        assertEquals(
            """{"refreshToken":"refresh-value"}""",
            json.encodeToString(RefreshRequest("refresh-value"))
        )

        val response = json.decodeFromString<TokenResponse>(
            """{"accessToken":"access-value","refreshToken":"refresh-value","deviceId":"device-1","sellerId":"seller-1","accessTokenExpiresAt":"2030-01-02T03:04:05Z"}"""
        )

        assertEquals("access-value", response.accessToken)
        assertEquals("refresh-value", response.refreshToken)
        assertEquals("device-1", response.deviceId)
        assertEquals("seller-1", response.sellerId)
        assertEquals(1_893_553_445_000L, response.accessTokenExpiresAtEpochMillis())
    }

    @Test
    fun backendHealthResponseReadsStatusAndIgnoresHealthGroups() {
        val response = json.decodeFromString<BackendHealthResponse>(
            """{"status":"UP","groups":["liveness","readiness"]}"""
        )

        assertEquals("UP", response.status)
    }

    @Test
    fun printAndPrinterStatusCallbacksUseBackendFieldNames() {
        assertEquals(
            """{"status":"FAILED","errorMessage":"Printer is offline"}""",
            json.encodeToString(JobStatusRequest("FAILED", "Printer is offline"))
        )
        assertEquals(
            """{"status":"CONNECTED"}""",
            json.encodeToString(PrinterStatusRequest("CONNECTED"))
        )
    }

    @Test
    fun printHistoryPageReadsContentAndIgnoresPagingMetadata() {
        val page = json.decodeFromString<PrintJobHistoryPage>(
            """{"content":[{"id":"job-1","payload":{"blocks":[]}}],"totalPages":2,"totalElements":21,"number":0,"size":20}"""
        )

        assertEquals("job-1", page.content.single().id)
        assertEquals(2, page.totalPages)
        assertEquals(21L, page.totalElements)
    }
}
