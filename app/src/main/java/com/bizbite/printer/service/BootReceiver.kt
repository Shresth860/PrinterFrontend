package com.bizbite.printer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!PrintForegroundService.start(context)) {
            Log.e(TAG, "Could not restart the foreground print service after ${intent.action}.")
        }
        val work = PeriodicWorkRequestBuilder<QueueSafetyWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            QueueSafetyWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private companion object { const val TAG = "BizBiteBootReceiver" }
}
