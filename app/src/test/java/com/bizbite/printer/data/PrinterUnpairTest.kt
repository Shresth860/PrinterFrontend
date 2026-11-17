package com.bizbite.printer.data

import com.bizbite.printer.data.local.LocalJobEntity
import com.bizbite.printer.data.local.PrinterDao
import com.bizbite.printer.data.local.PrinterEntity
import com.bizbite.printer.data.local.SettingsStore
import com.bizbite.printer.data.remote.PrintServiceApi
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.printer.PrinterConnectionFactory
import com.bizbite.printer.printer.ReceiptRenderer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Response

class PrinterUnpairTest {
    @Test
    fun successfulServerUnpairPrecedesLocalCredentialClearing() = runTest {
        val tokenStore = mockk<TokenStore>(relaxed = true)
        every { tokenStore.accessToken() } returns "device-access"
        val api = mockk<PrintServiceApi>()
        coEvery { api.unpair() } returns Response.success(Unit)
        val settings = mockk<SettingsStore>()
        val repository = repository(api, tokenStore, settings)

        repository.unpair()

        coVerifyOrder {
            api.unpair()
            tokenStore.clear()
        }
    }

    @Test
    fun failedServerUnpairStillClearsCredentialsAndSurfacesSafeFailure() = runTest {
        val tokenStore = mockk<TokenStore>(relaxed = true)
        every { tokenStore.accessToken() } returns "device-access"
        val api = mockk<PrintServiceApi>()
        coEvery { api.unpair() } returns Response.error(
            503,
            "unavailable".toResponseBody()
        )
        val settings = mockk<SettingsStore>()
        val repository = repository(api, tokenStore, settings)

        val failure = runCatching { repository.unpair() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("Server unpair failed. Local credentials were cleared.", failure?.message)
        verify(exactly = 1) { tokenStore.clear() }
    }

    @Test
    fun unpairWithoutAccessTokenClearsLocalStateWithoutCallingServer() = runTest {
        val tokenStore = mockk<TokenStore>(relaxed = true)
        every { tokenStore.accessToken() } returns null
        val api = mockk<PrintServiceApi>()
        val settings = mockk<SettingsStore>()
        val repository = repository(api, tokenStore, settings)

        repository.unpair()

        coVerify(exactly = 0) { api.unpair() }
        verify(exactly = 1) { tokenStore.clear() }
    }

    private fun repository(
        api: PrintServiceApi,
        tokenStore: TokenStore,
        settings: SettingsStore
    ): PrinterRepositoryImpl {
        val dao = mockk<PrinterDao>()
        every { dao.observePendingCount() } returns flowOf(0)
        every { dao.observeHistory() } returns emptyFlow<List<LocalJobEntity>>()
        every { dao.observeLastPrintAt() } returns flowOf(null)
        every { dao.observePrinters() } returns emptyFlow<List<PrinterEntity>>()
        every { dao.observeJobs() } returns emptyFlow<List<LocalJobEntity>>()
        return PrinterRepositoryImpl(
            dao,
            api,
            tokenStore,
            mockk<ReceiptRenderer>(),
            mockk<PrinterConnectionFactory>(),
            settings
        )
    }
}
