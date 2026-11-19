package com.bizbite.printer.data

import com.bizbite.printer.data.local.LocalJobEntity
import com.bizbite.printer.data.local.PrinterDao
import com.bizbite.printer.data.local.PrinterEntity
import com.bizbite.printer.data.local.PrinterStatusOutboxEntity
import com.bizbite.printer.data.local.SettingsStore
import com.bizbite.printer.data.local.StatusOutboxEntity
import com.bizbite.printer.BuildConfig
import com.bizbite.printer.data.remote.FcmTokenRequest
import com.bizbite.printer.data.remote.JobStatusRequest
import com.bizbite.printer.data.remote.PrinterStatusRequest
import com.bizbite.printer.data.remote.PairRequest
import com.bizbite.printer.data.remote.PrintServiceApi
import com.bizbite.printer.data.remote.PrinterRegistration
import com.bizbite.printer.data.remote.ReleaseInfo
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.domain.model.PrinterConfig
import com.bizbite.printer.domain.model.PrinterStatus
import com.bizbite.printer.domain.model.ConnectionType
import com.bizbite.printer.domain.queue.PrintQueuePolicy
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.printer.PrinterConnectionFactory
import com.bizbite.printer.printer.PartialWriteException
import com.bizbite.printer.printer.ReceiptRenderer
import com.bizbite.printer.printer.BluetoothSppConnection
import java.io.IOException
import java.util.UUID
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@Singleton
class PrinterRepositoryImpl @Inject constructor(
    private val dao: PrinterDao,
    private val api: PrintServiceApi,
    private val tokenStore: TokenStore,
    private val renderer: ReceiptRenderer,
    private val connectionFactory: PrinterConnectionFactory,
    private val settings: SettingsStore
) : PrinterRepository {
    private val consumerMutex = Mutex()
    override val pendingCount: Flow<Int> = dao.observePendingCount()
    override val history: Flow<List<PrintJob>> = dao.observeHistory().map { jobs -> jobs.map(LocalJobEntity::toModel) }
    override val lastPrintAt: Flow<Long?> = dao.observeLastPrintAt()
    override val printers: Flow<List<PrinterConfig>> = dao.observePrinters().map { list -> list.map(PrinterEntity::toModel) }
    override val jobs: Flow<List<PrintJob>> = dao.observeJobs().map { list -> list.map(LocalJobEntity::toModel) }

    override suspend fun isPaired() = !tokenStore.accessToken().isNullOrBlank()

    override suspend fun backendAvailable() = api.health().status.equals("UP", ignoreCase = true)

    override suspend fun pair(code: String) {
        require(code.length == 8 && code.all(Char::isLetterOrDigit)) { "The pairing code must contain 8 letters or digits." }
        val result = api.pair(
            PairRequest(
                code = code.uppercase(),
                deviceName = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME
            )
        )
        tokenStore.save(
            result.accessToken,
            result.refreshToken,
            result.accessTokenExpiresAtEpochMillis()
        )
    }

    override suspend fun fetchPending() {
        if (!isPaired()) return
        val printer = defaultPrinter() ?: return
        api.pendingJobs(printer.id).forEach { remote ->
            val payload = Json.parseToJsonElement(remote.payload.toString()).toString()
            val existing = dao.findJob(remote.id)
            if (!PrintQueuePolicy.shouldEnqueue(existing?.toModel())) return@forEach
            if (existing?.errorCode == "POSSIBLE_PARTIAL") return@forEach
            if (existing == null) {
                dao.insertJobIfAbsent(LocalJobEntity.from(PrintJob(remote.id, payload)))
            } else if (existing.status == JobStatus.FAILED.name || existing.status == JobStatus.PRINTING.name) {
                dao.upsertJob(LocalJobEntity.from(PrintJob(remote.id, payload, retryCount = existing.retryCount)))
            }
        }
    }

    override suspend fun recoverInterruptedJobs() {
        consumerMutex.withLock {
            dao.interruptedJobs().forEach { entity ->
                persistStatus(
                    entity.toModel().copy(
                        status = JobStatus.FAILED,
                        errorCode = "POSSIBLE_PARTIAL",
                        errorMessage = "The app stopped while this receipt was printing. Check printer output before reprinting."
                    )
                )
            }
        }
    }

    override suspend fun processNext(): PrintJob? = consumerMutex.withLock {
        val job = dao.pendingJobs().firstOrNull {
            it.status == JobStatus.PENDING.name &&
                it.retryCount < PrintQueuePolicy.MAX_ATTEMPTS &&
                !it.isSimulated
        } ?: return@withLock null
        var current = job.toModel().copy(
            status = JobStatus.PRINTING,
            errorCode = null,
            errorMessage = null,
            completedAt = null
        )
        val printer = dao.defaultPrinter()?.toModel()
        if (printer == null) {
            current = current.copy(status = JobStatus.FAILED, errorCode = "NO_PRINTER", errorMessage = "Choose a default printer.")
            persistStatus(current, reportedStatus = JobStatus.FAILED.name)
            return@withLock current
        }
        val maxAttempts = if (printer.type == ConnectionType.BLUETOOTH) {
            BLUETOOTH_MAX_ATTEMPTS
        } else {
            PrintQueuePolicy.MAX_ATTEMPTS
        }
        if (current.retryCount >= maxAttempts) {
            current = current.copy(
                status = JobStatus.FAILED,
                errorCode = "RETRY_LIMIT",
                errorMessage = "Print failed after $maxAttempts connection attempts."
            )
            persistStatus(current)
            return@withLock current
        }
        persistStatus(current)

        var connection: com.bizbite.printer.printer.PrinterConnection? = null
        var keepConnection = false
        var attempt = current.retryCount
        try {
          while (attempt < maxAttempts) {
            try {
                if (current.status != JobStatus.PRINTING) {
                    current = current.copy(status = JobStatus.PRINTING, errorCode = null, errorMessage = null)
                    persistStatus(current)
                }
                val active = connectionFactory.create(printer)
                connection = active
                if (!active.isConnected) active.connect()
                if (!active.isConnected) throw IOException("Printer socket did not connect.")
                enqueuePrinterStatus(printer.id, "CONNECTED")
                val copies = settings.copies.firstValue()
                val autoCut = settings.autoCut.firstValue()
                val cashDrawer = settings.cashDrawer.firstValue()
                val bytes = renderer.render(current.payload, printer.paperWidthMm, copies, autoCut, cashDrawer)
                Log.d(TAG, "Writing ${bytes.size} bytes to ${printer.type} printer.")
                active.write(bytes)
                keepConnection = active is BluetoothSppConnection
                current = current.copy(
                    status = JobStatus.SUCCESS,
                    errorCode = null,
                    errorMessage = null,
                    completedAt = System.currentTimeMillis()
                )
                persistStatus(current)
                return@withLock current
            } catch (failure: Exception) {
                Log.e(TAG, "Print attempt failed.", failure)
                val classification = classifyFailure(failure)
                if (classification == "POSSIBLE_PARTIAL") {
                    if (connection?.isConnected != true) enqueuePrinterStatus(printer.id, "DISCONNECTED")
                    val reason = failure.cause?.message?.take(120)
                    current = PrintQueuePolicy.failure(
                        current, classification,
                        "Connection lost after print data may have been sent${reason?.let { ": $it" } ?: ""}. " +
                            "Check the receipt before reprinting."
                    )
                    persistStatus(current, reportedStatus = JobStatus.FAILED.name)
                    return@withLock current
                }
                current = PrintQueuePolicy.failure(current, classification, failure.message?.take(240) ?: classification)
                if (current.retryCount >= maxAttempts && current.status != JobStatus.FAILED) {
                    current = current.copy(status = JobStatus.FAILED)
                }
                attempt = current.retryCount
                if (current.status == JobStatus.FAILED) {
                    persistStatus(current, reportedStatus = JobStatus.FAILED.name)
                    return@withLock current
                }
                persistStatus(current, reportedStatus = JobStatus.FAILED.name)
                try {
                    connection?.disconnect()
                } catch (disconnectFailure: Exception) {
                    Log.e(TAG, "Failed to close printer connection before retry.", disconnectFailure)
                }
                enqueuePrinterStatus(printer.id, "DISCONNECTED")
                connection = null
                kotlinx.coroutines.delay(PrintQueuePolicy.retryDelayMillis(current.retryCount))
            }
          }
        } finally {
            if (!keepConnection) {
                try {
                    connection?.disconnect()
                } catch (disconnectFailure: Exception) {
                    Log.e(TAG, "Failed to close printer connection after print.", disconnectFailure)
                }
                enqueuePrinterStatus(printer.id, "DISCONNECTED")
            }
        }
        current
    }

    override suspend fun syncOutbox() {
        dao.pendingOutbox().forEach { item ->
            val status = when (item.status) {
                JobStatus.PRINTING.name, JobStatus.SUCCESS.name, JobStatus.FAILED.name -> item.status
                JobStatus.PENDING.name -> JobStatus.FAILED.name
                else -> {
                    dao.deleteOutbox(item.id)
                    return@forEach
                }
            }
            val response = api.updateJobStatus(
                item.jobId,
                JobStatusRequest(status, item.errorMessage)
            )
            if (response.isSuccessful) {
                dao.deleteOutbox(item.id)
                dao.markStatusSynced(item.jobId)
            } else throw java.io.IOException("Status update was rejected (HTTP ${response.code()}).")
        }
        syncPrinterStatusOutbox()
    }

    private suspend fun syncPrinterStatusOutbox() {
        dao.pendingPrinterStatuses().forEach { item ->
            Log.d(TAG, "Sending printer status ${item.status} for ${item.printerId}.")
            val response = api.updatePrinterStatus(item.printerId, PrinterStatusRequest(item.status))
            if (response.isSuccessful) {
                dao.deletePrinterStatus(item.id)
                Log.d(TAG, "Printer status callback accepted.")
            } else {
                throw IOException("Printer status update was rejected (HTTP ${response.code()}).")
            }
        }
    }

    override suspend fun reportPrinterConnection(connected: Boolean, printerAddress: String?) {
        val printer = defaultPrinter() ?: return
        if (printerAddress != null && !printer.address.equals(printerAddress, ignoreCase = true)) return
        enqueuePrinterStatus(printer.id, if (connected) "CONNECTED" else "DISCONNECTED")
    }

    private suspend fun enqueuePrinterStatus(printerId: String, status: String) {
        Log.d(TAG, "Queueing printer status $status for $printerId.")
        dao.addPrinterStatus(
            PrinterStatusOutboxEntity(
                id = UUID.randomUUID().toString(),
                printerId = printerId,
                status = status
            )
        )
        try {
            syncPrinterStatusOutbox()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Printer status callback queued for retry.", failure)
        }
    }

 
override suspend fun syncPrinters() {
    dao.unsyncedPrinters().forEach { entity ->
        // Only 58mm and 80mm are supported. Repair invalid saved values to 58mm.
        val normalizedWidth = when (entity.paperWidthMm) {
            58, 80 -> entity.paperWidthMm
            else -> 58
        }

        // Persist the repaired value before syncing.
        val normalizedEntity = if (entity.paperWidthMm != normalizedWidth) {
            entity.copy(paperWidthMm = normalizedWidth).also {
                dao.upsertPrinter(it)
            }
        } else {
            entity
        }

        val response = api.savePrinter(
            PrinterRegistration(
                normalizedEntity.id,
                normalizedEntity.name,
                normalizedEntity.type,
                normalizedEntity.address,
                normalizedEntity.port,
                normalizedEntity.paperWidthMm,
                normalizedEntity.isDefault
            )
        )

        if (!response.isSuccessful) {
            throw java.io.IOException(
                "Printer configuration sync failed (HTTP ${response.code()})."
            )
        }

        dao.markPrinterSynced(normalizedEntity.id)
    }
}


    override suspend fun savePrinter(config: PrinterConfig) {
        require(config.type != com.bizbite.printer.domain.model.ConnectionType.MOCK) {
            "Virtual printers are not supported. Select a physical printer."
        }
        val entity = PrinterEntity.from(config)
        if (config.isDefault) dao.saveDefaultPrinter(entity) else dao.upsertPrinter(entity)
        syncPrinters()
    }

    override suspend fun defaultPrinter() = dao.defaultPrinter()?.toModel()

    override suspend fun enqueueTestPrint(): String {
        val printer = defaultPrinter() ?: error("Add and select a default printer first.")
        val remote = api.testPayload(printer.id)
        val remotePayload = (remote as? kotlinx.serialization.json.JsonObject)?.get("payload") ?: remote
        val payload = Json.parseToJsonElement(remotePayload.toString()).toString()
        val jobId = "$TEST_PRINT_ID_PREFIX${UUID.randomUUID()}"
        dao.insertJobIfAbsent(LocalJobEntity.from(PrintJob(jobId, payload)))
        Log.d(TAG, "Enqueued backend test payload in PENDING state.")
        return jobId
    }

    override suspend fun enqueueSellerPrint(jobId: String, payload: String): String {
        require(jobId.startsWith(SELLER_PRINT_ID_PREFIX))
        require(defaultPrinter() != null) { "Add and select a physical printer first." }
        val existing = dao.findJob(jobId)
        if (existing == null) {
            dao.insertJobIfAbsent(LocalJobEntity.from(PrintJob(jobId, payload)))
        } else {
            require(existing.status != JobStatus.PENDING.name && existing.status != JobStatus.PRINTING.name) {
                "This document already has a print in progress. Check local printer history."
            }
            require(existing.errorCode != "POSSIBLE_PARTIAL") {
                "The previous print may be partial. Check the paper before trying again."
            }
            dao.upsertJob(LocalJobEntity.from(PrintJob(jobId, payload)))
        }
        return jobId
    }

    override suspend fun printerStatus(): PrinterStatus {
        val printer = defaultPrinter() ?: return PrinterStatus.DISCONNECTED
        val connection = connectionFactory.create(printer)
        return try {
            if (!connection.isConnected) connection.connect()
            if (!connection.isConnected) return PrinterStatus.DISCONNECTED
            enqueuePrinterStatus(printer.id, "CONNECTED")
            val replies = (1..4).mapNotNull { status ->
                connection.write(byteArrayOf(0x10, 0x04, status.toByte()))
                connection.read(1, 500)?.firstOrNull()?.toInt()?.and(0xFF)
            }
            if (!connection.isConnected) PrinterStatus.DISCONNECTED
            else if (replies.isEmpty()) PrinterStatus.CONNECTED
            else when {
                replies.size >= 4 && (replies[3] and 0x08) != 0 -> PrinterStatus.PAPER_OUT
                replies.size >= 2 && (replies[1] and 0x04) != 0 -> PrinterStatus.COVER_OPEN
                (replies[0] and 0x08) != 0 -> PrinterStatus.DISCONNECTED
                else -> PrinterStatus.CONNECTED
            }
        } catch (failure: Exception) {
            Log.e(TAG, "Printer status check failed.", failure)
            enqueuePrinterStatus(printer.id, "DISCONNECTED")
            PrinterStatus.DISCONNECTED
        } finally {
            if (connection !is BluetoothSppConnection) {
                connection.disconnect()
                enqueuePrinterStatus(printer.id, "DISCONNECTED")
            } else if (!connection.isConnected) {
                enqueuePrinterStatus(printer.id, "DISCONNECTED")
            }
        }
    }

    override suspend fun setFcmToken(token: String) {
        requireSuccessful(api.updateFcmToken(FcmTokenRequest(token)), "FCM token update")
    }

    override suspend fun heartbeat() {
        requireSuccessful(api.heartbeat(), "Backend heartbeat")
    }

    override suspend fun latestRelease() = api.latestRelease()

    override suspend fun unpair() {
        var serverUnpairFailed = false
        try {
            if (!tokenStore.accessToken().isNullOrBlank()) {
                serverUnpairFailed = !api.unpair().isSuccessful
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            serverUnpairFailed = true
        } finally {
            tokenStore.clear()
        }
        if (serverUnpairFailed) {
            throw IOException("Server unpair failed. Local credentials were cleared.")
        }
    }

    override suspend fun reprint(job: PrintJob) {
        require(job.status == JobStatus.SUCCESS || job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED) {
            "Only completed or failed jobs can be reprinted."
        }
        dao.upsertJob(
            LocalJobEntity.from(
                job.copy(
                    id = "reprint-${UUID.randomUUID()}",
                    status = JobStatus.PENDING,
                    retryCount = 0,
                    errorCode = null,
                    errorMessage = null,
                    completedAt = null
                )
            )
        )
    }

    override suspend fun retry(job: PrintJob) {
        require(job.status == JobStatus.FAILED) { "Only failed jobs can be retried." }
        consumerMutex.withLock {
            dao.upsertJob(
                LocalJobEntity.from(
                    job.copy(
                        status = JobStatus.PENDING,
                        retryCount = 0,
                        errorCode = null,
                        errorMessage = null,
                        completedAt = null
                    )
                )
            )
        }
    }

    override suspend fun cancel(job: PrintJob) {
        consumerMutex.withLock {
            val current = dao.findJob(job.id)?.toModel() ?: error("Print job no longer exists.")
            require(current.status == JobStatus.PENDING) { "Only pending jobs can be cancelled." }
            persistStatus(current.copy(status = JobStatus.CANCELLED, errorCode = null, errorMessage = null))
        }
    }

    private suspend fun persistStatus(job: PrintJob, reportedStatus: String = job.status.name) {
        Log.d(TAG, "Persisting print job status ${job.status}, reporting $reportedStatus.")
        dao.upsertJob(LocalJobEntity.from(job))
        if (job.isSimulated || job.id.startsWith(TEST_PRINT_ID_PREFIX) || job.id.startsWith(SELLER_PRINT_ID_PREFIX) ||
            job.status !in setOf(JobStatus.PRINTING, JobStatus.SUCCESS, JobStatus.FAILED)
        ) return
        dao.addOutbox(
            StatusOutboxEntity(
                id = UUID.randomUUID().toString(),
                jobId = job.id,
                status = reportedStatus,
                errorCode = job.errorCode,
                errorMessage = job.errorMessage
            )
        )
        try {
            syncOutbox()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Job status callback queued for retry.", failure)
        }
    }

    private fun requireSuccessful(response: retrofit2.Response<*>, operation: String) {
        if (!response.isSuccessful) {
            throw IOException("$operation failed (HTTP ${response.code()}).")
        }
    }

    private fun classifyFailure(failure: Exception): String = when {
        failure is PartialWriteException -> "POSSIBLE_PARTIAL"
        failure is SecurityException -> "PERMISSION_DENIED"
        failure.message?.contains("PAPER_OUT", ignoreCase = true) == true -> "PAPER_OUT"
        failure.message?.contains("COVER_OPEN", ignoreCase = true) == true -> "COVER_OPEN"
        failure is java.net.SocketException -> "CONNECTION_LOST"
        failure is java.io.IOException -> "OFFLINE"
        else -> "UNKNOWN"
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    companion object {
        private const val TAG = "BizBiteRepository"
        private const val TEST_PRINT_ID_PREFIX = "test-print-"
        private const val SELLER_PRINT_ID_PREFIX = "bbn-print:"
        private const val BLUETOOTH_MAX_ATTEMPTS = 3
    }
}
