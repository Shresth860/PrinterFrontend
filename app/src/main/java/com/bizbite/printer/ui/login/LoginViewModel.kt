package com.bizbite.printer.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizbite.printer.data.auth.AuthRepository
import com.bizbite.printer.data.auth.AutoPairManager
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.session.SessionManager
import com.bizbite.printer.data.security.SellerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoginStep { LOGIN, SUCCESS }

data class LoginUiState(
    val phone: String = "",
    val pin: String = "",
    val step: LoginStep = LoginStep.LOGIN,
    val loading: Boolean = false,
    val error: String? = null
)

fun normalizePhoneInput(value: String): String = value.filter { it in '0'..'9' }.takeLast(10)
fun isValidPhone(value: String): Boolean = value.length == 10 && value.all { it in '0'..'9' }

fun loginErrorMessage(failure: Throwable): String = when (failure) {
    is BbnHttpException -> when (failure.statusCode) {
        401 -> "Invalid phone number or PIN."
        429 -> failure.message.takeIf(String::isNotBlank)
            ?: "Too many attempts. Try again after 15 minutes."
        else -> failure.message.takeIf(String::isNotBlank)
            ?: "Could not reach BizBiteNow. Check your connection and try again."
    }
    is IOException -> "Could not reach BizBiteNow. Check your connection and try again."
    else -> failure.message?.takeIf(String::isNotBlank)
        ?: "Could not reach BizBiteNow. Check your connection and try again."
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionManager,
    private val identity: SellerIdentityStore,
    private val autoPairManager: AutoPairManager
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state = mutableState.asStateFlow()

    fun phoneChanged(value: String) {
        mutableState.value = mutableState.value.copy(phone = normalizePhoneInput(value), error = null)
    }

    fun pinChanged(value: String) {
        mutableState.value = mutableState.value.copy(pin = value.filter { it in '0'..'9' }, error = null)
    }

    fun login() {
        val current = mutableState.value
        if (!isValidPhone(current.phone)) {
            mutableState.value = current.copy(error = "Enter a valid 10-digit phone number.")
            return
        }
        if (current.pin.isBlank()) {
            mutableState.value = current.copy(error = "Enter your PIN.")
            return
        }
        if (current.loading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                val result = auth.login(current.phone, current.pin)
                if (!result.isAgreed) {
                    mutableState.value = mutableState.value.copy(
                        error = "Seller agreement is not accepted. Complete the agreement in the BizBiteNow seller portal before signing in here."
                    )
                    return@launch
                }
                identity.save(result.sellerKey, result.user?.role, result.user?.phone, result.user?.name)
                session.login(result.token)
                viewModelScope.launch { autoPairManager.onSellerLogin(result.sellerKey) }
                mutableState.value = mutableState.value.copy(step = LoginStep.SUCCESS)
            } catch (failure: Exception) {
                mutableState.value = mutableState.value.copy(error = loginErrorMessage(failure).take(180))
            } finally {
                mutableState.value = mutableState.value.copy(loading = false)
            }
        }
    }
}
