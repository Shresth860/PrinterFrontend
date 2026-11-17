package com.bizbite.printer.domain.repository

import com.bizbite.printer.data.remote.ReleaseInfo
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.domain.model.PrinterConfig
import com.bizbite.printer.domain.model.PrinterStatus
import kotlinx.coroutines.flow.Flow

interface PrinterRepository {
    val pendingCount: Flow<Int>
    val history: Flow<List<PrintJob>>
    val lastPrintAt: Flow<Long?>
    val printers: Flow<List<PrinterConfig>>
    val jobs: Flow<List<PrintJob>>
    suspend fun isPaired(): Boolean
    suspend fun backendAvailable(): Boolean
    suspend fun pair(code: String)
    suspend fun fetchPending()
    suspend fun recoverInterruptedJobs()
    suspend fun processNext(): PrintJob?
    suspend fun syncOutbox()
    suspend fun reportPrinterConnection(connected: Boolean, printerAddress: String? = null)
    suspend fun syncPrinters()
    suspend fun savePrinter(config: PrinterConfig)
    suspend fun defaultPrinter(): PrinterConfig?
    suspend fun enqueueTestPrint(): String
    suspend fun enqueueSellerPrint(jobId: String, payload: String): String
    suspend fun retry(job: PrintJob)
    suspend fun cancel(job: PrintJob)
    suspend fun printerStatus(): PrinterStatus
    suspend fun setFcmToken(token: String)
    suspend fun heartbeat()
    suspend fun latestRelease(): ReleaseInfo
    suspend fun unpair()
    suspend fun reprint(job: PrintJob)
}
