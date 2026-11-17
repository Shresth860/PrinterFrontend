package com.bizbite.printer.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.json.JsonElement

interface PrintServiceApi {
    @GET("actuator/health")
    suspend fun health(): BackendHealthResponse

    @POST("api/v1/auth/pair")
    suspend fun pair(@Body request: PairRequest): TokenResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @DELETE("api/v1/devices/me")
    suspend fun unpair(): Response<Unit>

    @PUT("api/v1/devices/me/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<Unit>

    @POST("api/v1/devices/me/heartbeat")
    suspend fun heartbeat(): Response<Unit>

    @PUT("api/v1/devices/me/printers")
    suspend fun savePrinter(@Body printer: PrinterRegistration): Response<Unit>

    @GET("api/print-jobs/pending")
    suspend fun pendingJobs(@Query("printerId") printerId: String): List<JobResponse>

    @PATCH("api/print-jobs/{id}/status")
    suspend fun updateJobStatus(@Path("id") id: String, @Body request: JobStatusRequest): Response<Unit>

    @GET("api/print-jobs/history")
    suspend fun history(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PrintJobHistoryPage

    @GET("api/print-jobs/test-payload")
    suspend fun testPayload(@Query("printerId") printerId: String): JsonElement

    @PATCH("api/v1/devices/me/printers/{printerId}/status")
    suspend fun updatePrinterStatus(
        @Path("printerId") printerId: String,
        @Body request: PrinterStatusRequest
    ): Response<Unit>

    @GET("api/v1/app/latest-release")
    suspend fun latestRelease(): ReleaseInfo
}
