package com.bizbite.printer.printer

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class TcpConnection(private val host: String, private val port: Int) : PrinterConnection {
    private var socket: Socket? = null
    override val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (isConnected) return@withContext
        require(port in 1..65535) { "Invalid printer port." }
        val candidate = Socket()
        try {
            candidate.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            candidate.soTimeout = READ_TIMEOUT_MS
            socket = candidate
        } catch (failure: IOException) {
            runCatching { candidate.close() }
            throw failure
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val active = socket ?: throw IOException("LAN printer is disconnected.")
        var written = 0
        try {
            while (written < bytes.size) {
                val count = minOf(CHUNK_SIZE, bytes.size - written)
                try {
                    active.getOutputStream().write(bytes, written, count)
                    active.getOutputStream().flush()
                } catch (failure: IOException) {
                    throw PartialWriteException(written, failure)
                }
                written += count
                if (written < bytes.size) delay(CHUNK_DELAY_MS)
            }
        } catch (failure: IOException) {
            if (written > 0) throw PartialWriteException(written, failure)
            throw failure
        }
    }

    override suspend fun read(maxBytes: Int, timeoutMillis: Long): ByteArray? = withContext(Dispatchers.IO) {
        val active = socket ?: return@withContext null
        val originalTimeout = active.soTimeout
        try {
            active.soTimeout = timeoutMillis.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            val result = ByteArray(maxBytes)
            val size = active.getInputStream().read(result)
            if (size <= 0) null else result.copyOf(size)
        } catch (_: java.net.SocketTimeoutException) {
            null
        } finally {
            if (!active.isClosed) active.soTimeout = originalTimeout
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 750
        const val CHUNK_SIZE = 512
        const val CHUNK_DELAY_MS = 20L
    }
}
