package com.bizbite.printer.data

import com.bizbite.printer.data.local.PrinterDao
import com.bizbite.printer.data.local.PrinterEntity
import com.bizbite.printer.data.local.SettingsStore
import com.bizbite.printer.data.remote.PrintServiceApi
import com.bizbite.printer.data.remote.PrinterRegistration
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.printer.PrinterConnectionFactory
import com.bizbite.printer.printer.ReceiptRenderer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class PrinterRegistrationValidationTest {
    @Test fun invalidSavedWidthIsRepairedTo58BeforeSync() = runTest {
        val dao = mockk<PrinterDao>(relaxed = true)
        val api = mockk<PrintServiceApi>()
        var row = PrinterEntity("lan:1", "Kitchen", "LAN", "10.0.0.10", 9100, 0, true, false)
        coEvery { dao.allPrinters() } answers { listOf(row) }
        coEvery { dao.unsyncedPrinters() } answers { listOf(row) }
        coEvery { dao.upsertPrinter(any()) } coAnswers { row = firstArg() }
        coEvery { dao.markPrinterSynced(any()) } returns Unit
        val request = slot<PrinterRegistration>()
        coEvery { api.savePrinter(capture(request)) } returns Response.success(Unit)
        val repository = repository(dao, api)

        repository.syncPrinters()

        assertEquals(58, request.captured.paperWidthMm)
        coVerify(exactly = 1) { dao.upsertPrinter(match { it.paperWidthMm == 58 }) }
    }

    @Test fun width80Remains80() = runTest {
        val dao = mockk<PrinterDao>(relaxed = true)
        val api = mockk<PrintServiceApi>()
        val row = PrinterEntity("usb:1", "Kitchen", "USB", "usb-1", 0, 80, true, false)
        coEvery { dao.allPrinters() } returns listOf(row)
        coEvery { dao.unsyncedPrinters() } returns listOf(row)
        coEvery { dao.markPrinterSynced(any()) } returns Unit
        val request = slot<PrinterRegistration>()
        coEvery { api.savePrinter(capture(request)) } returns Response.success(Unit)

        repository(dao, api).syncPrinters()

        assertEquals(80, request.captured.paperWidthMm)
    }

    private fun repository(dao: PrinterDao, api: PrintServiceApi) = PrinterRepositoryImpl(
        dao,
        api,
        mockk<TokenStore>(relaxed = true),
        mockk<ReceiptRenderer>(relaxed = true),
        mockk<PrinterConnectionFactory>(relaxed = true),
        mockk<SettingsStore>(relaxed = true)
    )
}