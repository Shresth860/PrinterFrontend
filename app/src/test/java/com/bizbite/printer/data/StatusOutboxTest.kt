package com.bizbite.printer.data

import com.bizbite.printer.data.local.PrinterDao
import com.bizbite.printer.data.local.LocalJobEntity
import com.bizbite.printer.data.local.PrinterEntity
import com.bizbite.printer.data.local.SettingsStore
import com.bizbite.printer.data.local.StatusOutboxEntity
import com.bizbite.printer.data.local.PrinterStatusOutboxEntity
import com.bizbite.printer.data.remote.JobStatusRequest
import com.bizbite.printer.data.remote.BackendHealthResponse
import com.bizbite.printer.data.remote.PrintServiceApi
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.printer.PrinterConnectionFactory
import com.bizbite.printer.printer.ReceiptRenderer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import org.junit.Assert.assertEquals

class StatusOutboxTest {
    @Test
    fun successfulServerStatusUpdateRemovesOutboxEntryAndMarksJobSynced() = runTest {
        val dao = mockk<PrinterDao>()
        stubObservedFlows(dao)
        val api = mockk<PrintServiceApi>()
        val settings = mockk<SettingsStore>()
        val item = StatusOutboxEntity("out-1", "job-1", "SUCCESS", null, null)
        coEvery { dao.pendingOutbox() } returns listOf(item)
        coEvery { api.updateJobStatus("job-1", JobStatusRequest("SUCCESS", null)) } returns Response.success(Unit)
        coEvery { dao.deleteOutbox("out-1") } returns Unit
        coEvery { dao.markStatusSynced("job-1") } returns Unit
        coEvery { dao.pendingPrinterStatuses() } returns emptyList()
        val repository = PrinterRepositoryImpl(
            dao,
            api,
            mockk<TokenStore>(),
            mockk<ReceiptRenderer>(),
            mockk<PrinterConnectionFactory>(),
            settings
        )

        repository.syncOutbox()

        coVerify(exactly = 1) { dao.deleteOutbox("out-1") }
        coVerify(exactly = 1) { dao.markStatusSynced("job-1") }
    }

    @Test
    fun serverFailureLeavesOutboxEntryForRetry() = runTest {
        val dao = mockk<PrinterDao>()
        stubObservedFlows(dao)
        val api = mockk<PrintServiceApi>()
        val settings = mockk<SettingsStore>()
        val item = StatusOutboxEntity("out-2", "job-2", "FAILED", "OFFLINE", "No printer connection")
        coEvery { dao.pendingOutbox() } returns listOf(item)
        coEvery { api.updateJobStatus("job-2", JobStatusRequest("FAILED", "No printer connection")) } returns
            Response.error(503, "unavailable".toResponseBody())
        coEvery { dao.pendingPrinterStatuses() } returns emptyList()
        val repository = PrinterRepositoryImpl(
            dao, api, mockk(), mockk(), mockk(), settings
        )

        val failure = runCatching { repository.syncOutbox() }.exceptionOrNull()
        org.junit.Assert.assertTrue(failure is java.io.IOException)
        coVerify(exactly = 0) { dao.deleteOutbox("out-2") }
    }

    @Test
    fun failedPrinterStatusCallbackRemainsQueuedForRetry() = runTest {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        val dao = mockk<PrinterDao>()
        stubObservedFlows(dao)
        val api = mockk<PrintServiceApi>()
        val settings = mockk<SettingsStore>()
        val item = PrinterStatusOutboxEntity("status-1", "printer-1", "DISCONNECTED")
        coEvery { dao.pendingOutbox() } returns emptyList()
        coEvery { dao.pendingPrinterStatuses() } returns listOf(item)
        coEvery { api.updatePrinterStatus("printer-1", any()) } returns
            Response.error(503, "unavailable".toResponseBody())
        val repository = PrinterRepositoryImpl(
            dao, api, mockk(), mockk(), mockk(), settings
        )

        try {
            val failure = runCatching { repository.syncOutbox() }.exceptionOrNull()

            assertEquals("Printer status update was rejected (HTTP 503).", failure?.message)
            coVerify(exactly = 0) { dao.deletePrinterStatus("status-1") }
        } finally {
            unmockkStatic(android.util.Log::class)
        }
    }

    @Test
    fun unsuccessfulHeartbeatIsReportedAsFailure() = runTest {
        val dao = mockk<PrinterDao>()
        stubObservedFlows(dao)
        val api = mockk<PrintServiceApi>()
        coEvery { api.heartbeat() } returns
            Response.error(503, "unavailable".toResponseBody())
        val repository = PrinterRepositoryImpl(
            dao, api, mockk(), mockk(), mockk(), mockk()
        )

        val failure = runCatching { repository.heartbeat() }.exceptionOrNull()

        assertEquals("Backend heartbeat failed (HTTP 503).", failure?.message)
    }

    @Test
    fun unsuccessfulFcmRegistrationIsReportedAsFailure() = runTest {
        val dao = mockk<PrinterDao>()
        stubObservedFlows(dao)
        val api = mockk<PrintServiceApi>()
        coEvery { api.updateFcmToken(any()) } returns
            Response.error(401, "unauthorized".toResponseBody())
        val repository = PrinterRepositoryImpl(
            dao, api, mockk(), mockk(), mockk(), mockk()
        )

        val failure = runCatching { repository.setFcmToken("fcm-token") }.exceptionOrNull()

        assertEquals("FCM token update failed (HTTP 401).", failure?.message)
    }

    @Test
    fun backendHealthIsAvailableOnlyWhenStatusIsUp() = runTest {
        val dao = mockk<PrinterDao>()
        stubObservedFlows(dao)
        val api = mockk<PrintServiceApi>()
        coEvery { api.health() } returns BackendHealthResponse("DOWN")
        val repository = PrinterRepositoryImpl(
            dao, api, mockk(), mockk(), mockk(), mockk()
        )

        assertEquals(false, repository.backendAvailable())
    }

    private fun stubObservedFlows(dao: PrinterDao) {
        every { dao.observePendingCount() } returns flowOf(0)
        every { dao.observeHistory() } returns emptyFlow<List<LocalJobEntity>>()
        every { dao.observeLastPrintAt() } returns flowOf(null)
        every { dao.observePrinters() } returns emptyFlow<List<PrinterEntity>>()
        every { dao.observeJobs() } returns emptyFlow<List<LocalJobEntity>>()
    }
}
