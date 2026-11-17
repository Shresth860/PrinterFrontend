package com.bizbite.printer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bizbite.printer.data.remote.ReleaseInfo
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateInstaller(private val context: Context) {
    suspend fun downloadAndInstall(release: ReleaseInfo) = withContext(Dispatchers.IO) {
        val uri = Uri.parse(release.url)
        require(uri.scheme.equals("https", ignoreCase = true)) { "Updates must be downloaded over HTTPS." }
        require(release.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "The update checksum is invalid." }
        val directory = File(context.externalCacheDir ?: context.cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) error("Unable to create the update download directory.")
        val apk = File(directory, "bizbite-${release.versionCode}.apk")
        val connection = URL(release.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "Update download failed with HTTP ${connection.responseCode}." }
            require(connection.contentLengthLong <= MAX_DOWNLOAD_BYTES || connection.contentLengthLong < 0) {
                "The update package is too large."
            }
            connection.inputStream.use { input ->
                FileOutputStream(apk).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_DOWNLOAD_BYTES) { "The update package is too large." }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
        } catch (failure: Exception) {
            apk.delete()
            throw failure
        } finally {
            connection.disconnect()
        }

        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(release.sha256, ignoreCase = true)) {
            apk.delete()
            error("The downloaded update failed its integrity check.")
        }

        withContext(Dispatchers.Main) {
            if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                val permissionIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(permissionIntent)
                return@withContext
            }
            val contentUri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk
            )
            context.startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setData(contentUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 250L * 1024 * 1024
    }
}
