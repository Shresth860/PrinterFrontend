package com.bizbite.printer.domain.queue

import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob

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
        require(attempt in 1 until MAX_ATTEMPTS)
        return 1_000L shl (attempt - 1)
    }

    private val NON_RETRYABLE_ERRORS = setOf("PAPER_OUT", "COVER_OPEN", "PERMISSION_DENIED")
}
