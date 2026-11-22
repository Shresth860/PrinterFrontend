package com.bizbite.printer.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketResourceSlotTest {
    private class FakeResource(var closed: Boolean = false)

    @Test
    fun replacingSocketClosesOldStreamAndSocketBeforeKeepingOnlyTheNewPair() {
        val closed = mutableListOf<String>()
        val slot = SocketResourceSlot<FakeResource, FakeResource>(
            closeOutput = { stream, _ -> stream.closed = true; closed += "stream" },
            closeSocket = { socket, _ -> socket.closed = true; closed += "socket" }
        )
        val firstSocket = FakeResource()
        val firstStream = FakeResource()
        val secondSocket = FakeResource()
        val secondStream = FakeResource()

        slot.replace(firstSocket, firstStream, "first")
        slot.replace(secondSocket, secondStream, "reconnect")

        assertTrue(firstStream.closed)
        assertTrue(firstSocket.closed)
        assertEquals(listOf("stream", "socket"), closed)
        assertSame(secondSocket, slot.snapshot().first)
        assertSame(secondStream, slot.snapshot().second)
        assertTrue(slot.hasResources())

        slot.clear("done")
        assertTrue(secondStream.closed)
        assertTrue(secondSocket.closed)
        assertFalse(slot.hasResources())
    }
}
