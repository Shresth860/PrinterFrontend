package com.bizbite.printer.features.creditDebitNotes.utils

import java.util.concurrent.atomic.AtomicBoolean

/** Guards one create intent and keeps ambiguous network outcomes locked until status is confirmed. */
class CreateSubmissionGate {
    private val locked = AtomicBoolean(false)
    @Volatile private var ambiguous = false

    fun begin(): Boolean = !ambiguous && locked.compareAndSet(false, true)
    fun releaseKnownFailure() { locked.set(false) }
    fun markUnknown() { ambiguous = true }
    fun resolveUnknown() { ambiguous = false; locked.set(false) }
    fun isLocked(): Boolean = locked.get()
    fun isUnknown(): Boolean = ambiguous
}
