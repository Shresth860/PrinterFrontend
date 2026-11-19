package com.bizbite.printer.features.creditDebitNotes.store

import com.bizbite.printer.features.creditDebitNotes.types.CreditDebitNote
import javax.inject.Inject
import javax.inject.Singleton

/** Process-memory cache, scoped to a single seller and explicitly cleared on logout. */
@Singleton
class CreditDebitNotesCache @Inject constructor() {
    private var ownerId: String? = null
    private var cachedNotes: List<CreditDebitNote> = emptyList()

    @Synchronized fun get(sellerId: String?): List<CreditDebitNote> {
        if (sellerId.isNullOrBlank() || ownerId != sellerId) {
            clearLocked()
            return emptyList()
        }
        return cachedNotes
    }

    @Synchronized fun put(sellerId: String?, notes: List<CreditDebitNote>) {
        if (sellerId.isNullOrBlank()) {
            clearLocked()
            return
        }
        if (ownerId != sellerId) clearLocked()
        ownerId = sellerId
        cachedNotes = notes.toList()
    }

    @Synchronized fun clear() = clearLocked()

    private fun clearLocked() {
        ownerId = null
        cachedNotes = emptyList()
    }
}
