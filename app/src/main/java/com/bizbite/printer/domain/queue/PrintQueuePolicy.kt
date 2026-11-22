package com.bizbite.printer.domain.queue

import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob

data class PrintTransferCheck(
    val connectedBeforeWrite: Boolean,
    val connectedAfterWrite: Boolean,
    val bytesWritten: Int,
    val expectedBytes: Int,
    val flushSucceeded: Boolean,
    val writeException: Throwable? = null
)

object PrintSuccessPolicy {
    fun failureReason(check: PrintTransferCheck): String? = when {
        check.writeException != null -> "Printer write failed: ${check.writeException.message ?: "unknown error"}"
        !check.connectedBeforeWrite -> "Printer was disconnected before the receipt was sent."
        !check.flushSucceeded -> "Printer output could not be flushed."
        check.bytesWritten != check.expectedBytes -> "Only ${check.bytesWritten} of ${check.expectedBytes} receipt bytes were sent."
        !check.connectedAfterWrite -> "Printer disconnected before the receipt transfer completed."
        else -> null
    }
}

object PrintQueuePolicy {
    const val MAX_ATTEMPTS = 5

    fun shouldEnqueue(existing: PrintJob?): Boolean = when {
        existing == null -> true
        existing.status == JobStatus.SUCCESS || existing.status == JobStatus.COMPLETED ||
            existing.status == JobStatus.CANCELLED -> false
        existing.errorCode == "POSSIBLE_PARTIAL" -> false
        existing.status == JobStatus.FAILED && existing.retryCount >= MAX_ATTEMPTS -> false
        else -> true
    }

    fun failure(job: PrintJob, code: String, message: String?): PrintJob {
        val attempts = job.retryCount + 1
        val terminal = attempts >= MAX_ATTEMPTS || code in NON_RETRYABLE_ERRORS || code == "POSSIBLE_PARTIAL"
        return job.copy(
            status = if (terminal) JobStatus.FAILED else JobStatus.PENDING,
            retryCount = attempts,
            errorCode = code,
            errorMessage = message
        )
    }

    fun retryDelayMillis(attempt: Int): Long {
        require(attempt >= 1)
        return (2_000L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(60_000L)
    }

    private val NON_RETRYABLE_ERRORS = setOf("PAPER_OUT", "COVER_OPEN", "PERMISSION_DENIED")
}
