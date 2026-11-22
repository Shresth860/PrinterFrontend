package com.bizbite.printer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bizbite.printer.R
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class PrintForegroundService : Service() {
    @Inject lateinit var repository: PrinterRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pollJob: Job? = null
    private var foregroundStarted = false
    private var bluetoothReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
            val remote = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            serviceScope.launch {
                try {
                    repository.reportPrinterConnection(connected = false, printerAddress = remote.address)
                } catch (failure: SecurityException) {
                    Log.e(TAG, "Cannot report Bluetooth printer disconnect without permission.", failure)
                } catch (failure: Exception) {
                    Log.e(TAG, "Could not queue Bluetooth printer disconnect status.", failure)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, notification())
            foregroundStarted = true
        } catch (failure: SecurityException) {
            Log.e(TAG, "Could not start the foreground print service.", failure)
            stopSelf()
            return
        }
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED),
            ContextCompat.RECEIVER_EXPORTED
        )
        bluetoothReceiverRegistered = true
        observeConnectivity()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pollJob?.isActive == true) {
            wakeups.trySend(Unit)
            return START_STICKY
        }
        pollJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (repository.isPaired()) {
                        repository.recoverInterruptedJobs()
                        // Drain jobs already stored on this phone before waiting on backend calls.
                        repository.processNext()
                        try {
                            withTimeout(NETWORK_SYNC_TIMEOUT_MS) {
                                repository.fetchPending()
                                repository.syncOutbox()
                                repository.syncPrinters()
                                repository.heartbeat()
                            }
                        } catch (cancelled: CancellationException) {
                            if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                                Log.w(TAG, "Backend sync exceeded ${NETWORK_SYNC_TIMEOUT_MS}ms; local print queue will continue.")
                            } else throw cancelled
                        } catch (failure: Exception) {
                            Log.e(TAG, "Backend sync failed; local printing remains enabled.", failure)
                        }
                    } else {
                        stopSelf()
                        return@launch
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e(TAG, "Foreground queue poll failed.", failure)
                }
                withTimeoutOrNull(POLL_INTERVAL_MS) { wakeups.receive() }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            bluetoothReceiverRegistered = false
        }
        networkCallback?.let { callback ->
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(callback)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeConnectivity() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    try {
                        repository.syncOutbox()
                        repository.syncPrinters()
                        repository.fetchPending()
                    } catch (failure: Exception) {
                        Log.e(TAG, "Network recovery sync failed.", failure)
                    }
                }
            }
        }
        networkCallback = callback
        manager.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback
        )
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Print service is running")
            .setSmallIcon(R.drawable.ic_printer)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Print service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_FETCH = "com.bizbite.printer.FETCH_JOBS"
        const val ACTION_PRINT_QUEUE = "com.bizbite.printer.PRINT_QUEUE"
        private const val CHANNEL_ID = "bizbite_print_service"
        private const val NOTIFICATION_ID = 101
        private const val POLL_INTERVAL_MS = 15_000L
        private const val NETWORK_SYNC_TIMEOUT_MS = 10_000L
        private const val TAG = "BizBitePrintService"

        fun start(context: Context, action: String = ACTION_FETCH): Boolean {
            val intent = Intent(context, PrintForegroundService::class.java).setAction(action)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (failure: SecurityException) {
                Log.e(TAG, "Android denied foreground print service startup.", failure)
                false
            }
        }
    }
}
