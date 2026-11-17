package com.bizbite.printer.domain.queue

import app.cash.turbine.test
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintQueuePolicyTest {
    @Test
    fun duplicateSuccessfulJobIsNeverQueued() {
        assertFalse(PrintQueuePolicy.shouldEnqueue(PrintJob("job-1", "{}", JobStatus.SUCCESS)))
        assertTrue(PrintQueuePolicy.shouldEnqueue(PrintJob("job-1", "{}", JobStatus.FAILED)))
        assertFalse(PrintQueuePolicy.shouldEnqueue(PrintJob("job-1", "{}", JobStatus.FAILED, errorCode = "POSSIBLE_PARTIAL")))
        assertTrue(PrintQueuePolicy.shouldEnqueue(null))
    }

    @Test
    fun retriesUseOneTwoFourEightSecondBackoffThenFailOnFifthAttempt() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), (1..4).map(PrintQueuePolicy::retryDelayMillis))
        var job = PrintJob("job-2", "{}")
        repeat(4) {
            job = PrintQueuePolicy.failure(job, "OFFLINE", "offline")
            assertEquals(JobStatus.PENDING, job.status)
        }
        job = PrintQueuePolicy.failure(job, "OFFLINE", "offline")
        assertEquals(JobStatus.FAILED, job.status)
        assertEquals(5, job.retryCount)
    }

    @Test
    fun paperOutAndPartialTransferDoNotRetryAutomatically() {
        assertEquals(JobStatus.FAILED, PrintQueuePolicy.failure(PrintJob("a", "{}"), "PAPER_OUT", null).status)
        assertEquals(JobStatus.FAILED, PrintQueuePolicy.failure(PrintJob("b", "{}"), "POSSIBLE_PARTIAL", null).status)
        assertEquals(JobStatus.FAILED, PrintQueuePolicy.failure(PrintJob("c", "{}"), "PERMISSION_DENIED", null).status)
    }

    @Test
    fun exposesQueueStateChangesAsFlow() = runTest {
        val state = MutableStateFlow(0)
        state.test {
            assertEquals(0, awaitItem())
            state.value = 2
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
