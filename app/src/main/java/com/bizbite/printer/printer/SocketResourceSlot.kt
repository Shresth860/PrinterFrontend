package com.bizbite.printer.printer

/** Owns one socket and its matching output stream, closing a previous pair before replacement. */
internal class SocketResourceSlot<S : Any, O : Any>(
    private val closeOutput: (O, String) -> Unit,
    private val closeSocket: (S, String) -> Unit
) {
    private var activeSocket: S? = null
    private var activeOutput: O? = null

    @Synchronized
    fun snapshot(): Pair<S?, O?> = activeSocket to activeOutput

    @Synchronized
    fun hasResources(): Boolean = activeSocket != null || activeOutput != null

    @Synchronized
    fun replace(socket: S, output: O, reason: String) {
        closeLocked(reason)
        activeOutput = output
        activeSocket = socket
    }

    @Synchronized
    fun clear(reason: String) = closeLocked(reason)

    private fun closeLocked(reason: String) {
        val output = activeOutput
        val socket = activeSocket
        activeOutput = null
        activeSocket = null
        if (output != null) runCatching { closeOutput(output, reason) }
        if (socket != null) runCatching { closeSocket(socket, reason) }
    }
}
