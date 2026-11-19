
package com.bizbite.printer.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bizbite.printer.data.local.SettingsStore
import com.bizbite.printer.data.auth.AutoPairManager
import com.bizbite.printer.data.auth.AutoPairState
import com.bizbite.printer.data.security.SellerSessionStore
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.domain.model.PrinterConfig
import com.bizbite.printer.domain.model.PrinterStatus
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.service.PrintForegroundService
import com.bizbite.printer.service.QueueSafetyWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: PrinterRepository,
    val settings: SettingsStore,
    private val autoPairManager: AutoPairManager,
    private val sellerSession: SellerSessionStore,
    private val sellerIdentity: SellerIdentityStore
) : AndroidViewModel(application) {

    val pendingCount = repository.pendingCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0
    )

    val history = repository.history.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val lastPrintAt = repository.lastPrintAt.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val printers = repository.printers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val jobs = repository.jobs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val lastPrintError = repository.jobs.map { jobs ->
        jobs.asSequence()
            .filter { it.status == com.bizbite.printer.domain.model.JobStatus.FAILED }
            .maxByOrNull { it.createdAt }
            ?.errorMessage
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val paired = MutableStateFlow<Boolean?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val update = MutableStateFlow<com.bizbite.printer.data.remote.ReleaseInfo?>(null)
    val internet = MutableStateFlow(false)
    val backendAvailable = MutableStateFlow<Boolean?>(null)
    val printerStatus = MutableStateFlow(PrinterStatus.UNKNOWN)
    val autoPairState = autoPairManager.state

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
                intent.action != BluetoothDevice.ACTION_ACL_CONNECTED
            ) return
            val remote = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            viewModelScope.launch {
                try {
                    if (repository.defaultPrinter()?.address != remote.address) return@launch
                    if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                        printerStatus.value = PrinterStatus.DISCONNECTED
                    } else {
                        refreshPrinterStatus()
                    }
                } catch (failure: SecurityException) {
                    Log.e(TAG, "Could not read Bluetooth ACL event details.", failure)
                    printerStatus.value = PrinterStatus.DISCONNECTED
                }
            }
        }
    }

    init {
        autoPairManager.startObservingAuthInvalidations()
        ContextCompat.registerReceiver(
            application,
            bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        viewModelScope.launch {
            while (isActive) {
                updateBackendAvailability()
                delay(BACKEND_HEALTH_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            paired.value = repository.isPaired()
            internet.value = isInternetAvailable()

            if (!sellerSession.token().isNullOrBlank()) {
                autoPairManager.onSellerLogin(sellerIdentity.id())
                paired.value = repository.isPaired()
            }

            if (paired.value == true) {
                checkForUpdate()
                refreshPrinterStatus()
            }

            val periodic =
                PeriodicWorkRequestBuilder<QueueSafetyWorker>(
                    15,
                    TimeUnit.MINUTES
                ).build()

            WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
                QueueSafetyWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )

            val updateSchedule =
                PeriodicWorkRequestBuilder<com.bizbite.printer.service.LatestReleaseWorker>(
                    6,
                    TimeUnit.HOURS
                ).build()

            WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
                com.bizbite.printer.service.LatestReleaseWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                updateSchedule
            )
        }
    }

    // --------------------------------------------------
    // ACCOUNT PAIRING
    // --------------------------------------------------

    fun retryPairing() = viewModelScope.launch {
        val result = autoPairManager.ensurePaired()
        paired.value = repository.isPaired()
        if (result is AutoPairState.Failed) message.value = result.reason
    }

    // --------------------------------------------------
    // PRINTER MANAGEMENT
    // --------------------------------------------------

    fun savePrinter(config: PrinterConfig) = action("Printer saved") {
        repository.savePrinter(config)
    }

    // --------------------------------------------------
    // PRINT JOB MANAGEMENT
    // --------------------------------------------------

    fun reprint(job: PrintJob) = action("Job queued for reprint") {
        repository.reprint(job)
    }

    fun retry(job: PrintJob) = action("Failed job queued for retry") {
        repository.retry(job)
    }

    fun cancel(job: PrintJob) = action("Pending job cancelled") {
        repository.cancel(job)
    }

    fun testPrint() = viewModelScope.launch {
        busy.value = true
        message.value = null
        try {
            val jobId = repository.enqueueTestPrint()
            if (!PrintForegroundService.start(
                    getApplication(),
                    PrintForegroundService.ACTION_PRINT_QUEUE
                )
            ) {
                throw SecurityException("Android did not allow the print service to start.")
            }
            message.value = "Test print queued."
            val result = repository.jobs
                .mapNotNull { list ->
                    list.firstOrNull { it.id == jobId }
                        ?.takeIf {
                            it.status == com.bizbite.printer.domain.model.JobStatus.SUCCESS ||
                                it.status == com.bizbite.printer.domain.model.JobStatus.FAILED
                        }
                }
                .first()
            if (result.status == com.bizbite.printer.domain.model.JobStatus.SUCCESS) {
                message.value = "Test print completed."
                refreshPrinterStatus()
            } else {
                message.value = "Print failed: ${result.errorMessage ?: result.errorCode ?: "Unknown error"}"
                printerStatus.value = PrinterStatus.DISCONNECTED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message.value = failure.message?.take(200) ?: "The test print could not be queued."
        } finally {
            busy.value = false
        }
    }

    // --------------------------------------------------
    // PRINTER STATUS
    // --------------------------------------------------

    fun refreshPrinterStatus() = viewModelScope.launch {
        printerStatus.value = repository.printerStatus()
    }

    // --------------------------------------------------
    // APP UPDATES
    // --------------------------------------------------

    fun checkForUpdate() = viewModelScope.launch {
        runCatching {
            val release = repository.latestRelease()

            val current = getApplication<Application>()
                .packageManager
                .getPackageInfo(
                    getApplication<Application>().packageName,
                    0
                )
                .longVersionCode

            if (release.versionCode > current) {
                update.value = release
            }
        }.onFailure {
            message.value = "Could not check for app updates."
        }
    }

    fun dismissUpdate() {
        update.value = null
    }

    fun downloadAndInstallUpdate(
        release: com.bizbite.printer.data.remote.ReleaseInfo
    ) = action("Downloading update") {
        com.bizbite.printer.update.UpdateInstaller(
            getApplication()
        ).downloadAndInstall(release)

        update.value = null
    }

    // --------------------------------------------------
    // UNPAIR
    // --------------------------------------------------

    fun unpair() = action("Unpaired") {
        try {
            repository.unpair()
        } finally {
            getApplication<Application>().stopService(
                Intent(
                    getApplication(),
                    PrintForegroundService::class.java
                )
            )

            paired.value = false
        }
    }

    private suspend fun updateBackendAvailability() {
        backendAvailable.value = try {
            repository.backendAvailable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    // --------------------------------------------------
    // UI MESSAGE
    // --------------------------------------------------

    fun clearMessage() {
        message.value = null
    }

    fun showMessage(value: String) {
        message.value = value
    }

    // --------------------------------------------------
    // NETWORK STATUS
    // --------------------------------------------------

    private fun isInternetAvailable(): Boolean {
        val manager = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = manager.activeNetwork ?: return false

        val capabilities = manager.getNetworkCapabilities(network)
            ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) && capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
    }

    // --------------------------------------------------
    // COMMON ACTION HANDLER
    // --------------------------------------------------

    private fun action(
        successMessage: String,
        block: suspend () -> Unit
    ) = viewModelScope.launch {

        busy.value = true
        message.value = null

        try {
            block()

            if (successMessage.isNotBlank() && message.value == null) {
                message.value = successMessage
            }

        } catch (failure: Exception) {

            message.value = failure.message?.take(200)
                ?: "The action could not be completed."

        } finally {
            busy.value = false
        }
    }

        override fun onCleared() {
            getApplication<Application>().unregisterReceiver(bluetoothReceiver)
            super.onCleared()
        }

    companion object {
        private const val BACKEND_HEALTH_INTERVAL_MS = 60_000L
            private const val TAG = "BizBiteViewModel"
    }
}
