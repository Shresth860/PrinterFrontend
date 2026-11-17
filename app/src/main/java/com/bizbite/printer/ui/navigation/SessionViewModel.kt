package com.bizbite.printer.ui.navigation

import androidx.lifecycle.ViewModel
import com.bizbite.printer.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SessionViewModel @Inject constructor(private val session: SessionManager) : ViewModel() {
    val isLoggedIn: StateFlow<Boolean> = session.isLoggedIn
    fun logout() = session.logout()
}
