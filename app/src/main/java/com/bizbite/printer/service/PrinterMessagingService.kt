package com.bizbite.printer.service

import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

class PrinterMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "NEW_JOB") return
        try {
            if (!PrintForegroundService.start(this)) enqueueFallback()
        } catch (_: Exception) {
            enqueueFallback()
        }
    }

    override fun onNewToken(token: String) {
        enqueueFallback(token)
    }

    private fun enqueueFallback(token: String? = null) {
        val request = OneTimeWorkRequestBuilder<QueueSafetyWorker>()
            .setInputData(androidx.work.workDataOf(QueueSafetyWorker.FCM_TOKEN_KEY to token))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            QueueSafetyWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
