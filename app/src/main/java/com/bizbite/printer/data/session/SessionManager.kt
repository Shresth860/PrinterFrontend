package com.bizbite.printer.data.session

import com.bizbite.printer.data.security.SellerSessionStore
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.features.creditDebitNotes.store.CreditDebitNotesCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionManager {
    val isLoggedIn: StateFlow<Boolean>
    fun login(token: String)
    fun logout()
}

@Singleton
class PersistentSessionManager @Inject constructor(
    private val store: SellerSessionStore,
    private val identity: SellerIdentityStore,
    private val notesCache: CreditDebitNotesCache
) : SessionManager {
    private val loggedIn = MutableStateFlow(!store.token().isNullOrBlank())
    override val isLoggedIn: StateFlow<Boolean> = loggedIn.asStateFlow()
    private var token: String? = null

    override fun login(token: String) {
        require(token.isNotBlank()) { "Seller session token cannot be blank." }
        store.save(token)
        this.token = token
        loggedIn.value = token.isNotBlank()
    }

    override fun logout() {
        notesCache.clear()
        store.clear()
        identity.clear()
        token = null
        loggedIn.value = false
    }
}
