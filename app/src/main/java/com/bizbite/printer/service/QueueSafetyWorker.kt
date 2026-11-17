package com.bizbite.printer.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bizbite.printer.domain.repository.PrinterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class QueueSafetyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PrinterRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (repository.isPaired()) {
            inputData.getString(FCM_TOKEN_KEY)?.takeIf(String::isNotBlank)?.let { repository.setFcmToken(it) }
            repository.recoverInterruptedJobs()
            repository.syncOutbox()
            repository.syncPrinters()
            repository.fetchPending()
            repository.processNext()
            Result.success()
        } else Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (runAttemptCount >= 5) Result.failure() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "print-queue-safety"
        const val FCM_TOKEN_KEY = "fcm_token"
    }
}
