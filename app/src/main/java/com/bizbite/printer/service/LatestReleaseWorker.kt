package com.bizbite.printer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bizbite.printer.R
import com.bizbite.printer.data.remote.ReleaseInfo
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class LatestReleaseWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PrinterRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            if (!repository.isPaired()) {
                Result.success()
            } else {
                val release = repository.latestRelease()
                val current = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
                if (release.versionCode > current) notifyUpdate(release)
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private fun notifyUpdate(release: ReleaseInfo) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = android.app.PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            UPDATE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_printer)
                .setContentTitle(if (release.mandatory) "Required update available" else "BizBite Printer update")
                .setContentText("Version ${release.versionName} is ready to install.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val UNIQUE_NAME = "latest-app-release"
        private const val CHANNEL_ID = "bizbite_updates"
        private const val UPDATE_NOTIFICATION_ID = 102
    }
}
