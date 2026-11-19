package com.bizbite.printer.data.auth

import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.security.SellerSessionStore
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.data.security.PairedSellerStore
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.data.session.SessionManager
import com.bizbite.printer.domain.repository.PrinterRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

sealed interface AutoPairState {
    data object NotPaired : AutoPairState
    data object Pairing : AutoPairState
    data object Paired : AutoPairState
    data class Failed(val reason: String, val retryable: Boolean) : AutoPairState
}

@Singleton
class AutoPairManager @Inject constructor(
    private val api: BbnApi,
    private val printerRepository: PrinterRepository,
    private val tokenStore: TokenStore,
    private val sellerSession: SellerSessionStore,
    private val sellerIdentity: SellerIdentityStore,
    private val pairedSellerStore: PairedSellerStore,
    private val sessionManager: SessionManager
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<AutoPairState>(AutoPairState.NotPaired)
    val state = mutableState.asStateFlow()
    private val rePairGuard = AutoPairLoopGuard(LOOP_WINDOW_MS, MAX_REPAIRS_IN_WINDOW)
    @Volatile private var observing = false

    fun startObservingAuthInvalidations() {
        if (observing) return
        synchronized(this) {
            if (observing) return
            observing = true
        }
        scope.launch {
            tokenStore.authInvalidations.drop(1).collect {
                if (sellerSession.token().isNullOrBlank()) return@collect
                if (!rePairGuard.allow(System.currentTimeMillis())) {
                    mutableState.value = AutoPairState.Failed("Printer authorization repeatedly expired. Retry after a few minutes.", true)
                    return@collect
                }
                ensurePaired()
            }
        }
    }

    suspend fun onSellerLogin(sellerKey: String?): AutoPairState = mutex.withLock {
        if (sellerSession.token().isNullOrBlank()) return@withLock fail("Sign in again to connect the printer.", false)
        val key = sellerKey?.takeIf(String::isNotBlank)
            ?: return@withLock fail("Seller identity is missing; printer setup cannot continue.", false)
        val priorSeller = pairedSellerStore.sellerKey()
        if (priorSeller != null && priorSeller != key) {
            try {
                printerRepository.unpair()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best effort server unpair; repository clears the local device credentials in its finally block.
            }
            tokenStore.clear()
            pairedSellerStore.clear()
        }
        ensurePairedLocked(key)
    }

    suspend fun ensurePaired(): AutoPairState = mutex.withLock {
        if (sellerSession.token().isNullOrBlank()) return@withLock fail("Sign in to connect the printer.", false)
        ensurePairedLocked(pairedSellerStore.sellerKey() ?: sellerIdentity.id())
    }

    private suspend fun ensurePairedLocked(sellerKey: String?): AutoPairState {
        if (sellerSession.token().isNullOrBlank()) return fail("Sign in to connect the printer.", false)
        if (hasDeviceCredentials()) {
            mutableState.value = AutoPairState.Pairing
            return try {
                printerRepository.syncPrinters()
                mutableState.value = AutoPairState.Paired
                AutoPairState.Paired
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                fail(safeReason(failure), isRetryable(failure))
            }
        }
        if (sellerKey.isNullOrBlank()) return fail("Seller identity is missing; printer setup cannot continue.", false)

        mutableState.value = AutoPairState.Pairing
        var lastFailure: Throwable? = null
        var invalidCodeRetried = false
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                if (sellerSession.token().isNullOrBlank() || !sessionManager.isLoggedIn.value) {
                    return fail("Seller session expired. Sign in again.", false)
                }
                val response = withTimeout(PAIRING_CODE_TIMEOUT_MS) { api.createPairingCode() }
                if (!response.success) throw PairingSetupException(response.message ?: "Could not create a printer pairing code.", false)
                val code = response.code?.takeIf { it.length == 8 && it.all(Char::isLetterOrDigit) }
                    ?: throw PairingSetupException("The server returned an invalid printer pairing code.", false)
                try {
                    printerRepository.pair(code)
                } catch (failure: Throwable) {
                    if (httpStatus(failure) == 422 && !invalidCodeRetried) {
                        invalidCodeRetried = true
                        lastFailure = failure
                        continue
                    }
                    throw failure
                }
                pairedSellerStore.save(sellerKey)
                try {
                    printerRepository.syncPrinters()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    return fail(safeReason(failure), isRetryable(failure))
                }
                mutableState.value = AutoPairState.Paired
                return AutoPairState.Paired
            } catch (timeout: TimeoutCancellationException) {
                lastFailure = timeout
                if (attempt == MAX_ATTEMPTS - 1) return fail(safeReason(timeout), true)
                delay(BACKOFF_MS[attempt])
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                lastFailure = failure
                if (failure is BbnHttpException && failure.statusCode == 401) {
                    // BBN interceptor owns the existing seller logout behavior; never retry pairing.
                    return fail("Seller session expired. Sign in again.", false)
                }
                if (!isRetryable(failure) || attempt == MAX_ATTEMPTS - 1) {
                    return fail(safeReason(failure), isRetryable(failure))
                }
                delay(BACKOFF_MS[attempt])
            }
        }
        return fail(safeReason(lastFailure), true)
    }

    private fun hasDeviceCredentials(): Boolean =
        !tokenStore.accessToken().isNullOrBlank() && !tokenStore.refreshToken().isNullOrBlank()

    private fun fail(reason: String, retryable: Boolean): AutoPairState.Failed {
        return AutoPairState.Failed(reason, retryable).also { mutableState.value = it }
    }

    private fun httpStatus(failure: Throwable): Int? = when (failure) {
        is BbnHttpException -> failure.statusCode
        is HttpException -> failure.code()
        else -> failure.cause?.let(::httpStatus)
    }

    private fun isRetryable(failure: Throwable): Boolean = when (failure) {
        is PairingSetupException -> failure.canRetry
        is BbnHttpException -> failure.statusCode in setOf(500, 502, 503, 504)
        is HttpException -> failure.code() in setOf(408, 429, 500, 502, 503, 504)
        is TimeoutCancellationException, is IOException -> true
        else -> false
    }

    private fun safeReason(failure: Throwable?): String = when (httpStatus(failure ?: Exception())) {
        502, 503, 504 -> "Printer setup server is starting or temporarily unavailable. Try again."
        422 -> "The pairing code was rejected. Try pairing again."
        else -> if (failure is TimeoutCancellationException) "Printer setup timed out. Try again."
        else if (failure is IOException) "Network problem while setting up the printer. Check connection and retry."
        else (failure?.message?.takeIf(String::isNotBlank) ?: "Printer setup failed. Try again.").take(180)
    }

    private class PairingSetupException(message: String, val canRetry: Boolean) : IOException(message)

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val MAX_REPAIRS_IN_WINDOW = 3
        const val LOOP_WINDOW_MS = 5 * 60 * 1000L
        const val PAIRING_CODE_TIMEOUT_MS = 30_000L
        val BACKOFF_MS = longArrayOf(1_000L, 2_000L)
    }
}

internal class AutoPairLoopGuard(private val windowMs: Long, private val maxAttempts: Int) {
    private val attempts = ArrayDeque<Long>()
    @Synchronized fun allow(now: Long): Boolean {
        while (attempts.isNotEmpty() && now - attempts.first() >= windowMs) attempts.removeFirst()
        if (attempts.size >= maxAttempts) return false
        attempts.addLast(now)
        return true
    }
}
