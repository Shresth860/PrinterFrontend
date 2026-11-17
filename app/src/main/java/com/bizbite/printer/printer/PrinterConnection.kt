package com.bizbite.printer.printer

import com.bizbite.printer.domain.model.PrinterConfig
import java.io.IOException

interface PrinterConnection {
    val isConnected: Boolean
    suspend fun connect()
    suspend fun disconnect()
    suspend fun write(bytes: ByteArray)
    suspend fun read(maxBytes: Int, timeoutMillis: Long = 750): ByteArray?
}

class PartialWriteException(val bytesWritten: Int, cause: Throwable) :
    IOException("Printer connection lost after data transfer began.", cause)

fun interface PrinterConnectionFactory {
    suspend fun create(printer: PrinterConfig): PrinterConnection
}
