package com.bizbite.printer.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

@Serializable
data class PairRequest(
    val code: String,
    val deviceName: String,
    val androidVersion: String,
    val appVersion: String,
    val fcmToken: String? = null
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val deviceId: String,
    val sellerId: String,
    val accessTokenExpiresAt: String
) {
    fun accessTokenExpiresAtEpochMillis(): Long = Instant.parse(accessTokenExpiresAt).toEpochMilli()
}

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class JobResponse(val id: String, val payload: JsonElement)

@Serializable
data class JobStatusRequest(
    val status: String,
    val errorMessage: String? = null
)

@Serializable
data class PrintJobHistoryPage(
    val content: List<JobResponse> = emptyList(),
    val totalPages: Int = 0,
    val totalElements: Long = 0
)

@Serializable
data class PrinterStatusRequest(val status: String)

@Serializable
data class PrinterRegistration(
    val id: String,
    val name: String,
    val type: String,
    val address: String,
    val port: Int,
    @SerialName("paper_width_mm") val paperWidthMm: Int,
    @SerialName("is_default") val isDefault: Boolean
)

@Serializable
data class FcmTokenRequest(@SerialName("fcm_token") val token: String)

@Serializable
data class BackendHealthResponse(val status: String)

@Serializable
data class ReleaseInfo(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    val url: String,
    @SerialName("sha256") val sha256: String,
    val mandatory: Boolean
)
