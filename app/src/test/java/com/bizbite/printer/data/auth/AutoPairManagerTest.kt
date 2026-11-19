package com.bizbite.printer.data.auth

import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.remote.PairingCodeResponse
import com.bizbite.printer.data.remote.TokenResponse
import com.bizbite.printer.data.security.PairedSellerStore
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.data.security.SellerSessionStore
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.data.session.SessionManager
import com.bizbite.printer.domain.repository.PrinterRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.atomic.AtomicReference

class AutoPairManagerTest {
    @Test fun alreadyPairedSkipsCodeCreationAndPairCall() = runTest {
        val f = fixture(access = "device-access", refresh = "device-refresh", owner = "seller-1")
        assertEquals(AutoPairState.Paired, f.manager.ensurePaired())
        coVerify(exactly = 0) { f.api.createPairingCode() }
        coVerify(exactly = 0) { f.repository.pair(any()) }
    }

    @Test fun successfulPairStoresSellerOwnership() = runTest {
        val f = fixture()
        coEvery { f.repository.pair("12345678") } coAnswers {
            f.access.set("device-access"); f.refresh.set("device-refresh")
        }
        assertEquals(AutoPairState.Paired, f.manager.onSellerLogin("seller-1"))
        coVerify(exactly = 1) { f.api.createPairingCode() }
        coVerify(exactly = 1) { f.repository.pair("12345678") }
        verify(exactly = 1) { f.pairedSellerStore.save("seller-1") }
    }

    @Test fun rejectedCodeGetsOneFreshCode() = runTest {
        val f = fixture(codes = listOf("11111111", "22222222"))
        var pairs = 0
        coEvery { f.repository.pair(any()) } coAnswers {
            pairs++
            if (pairs == 1) throw HttpException(Response.error<TokenResponse>(422, "expired".toResponseBody()))
            f.access.set("device-access"); f.refresh.set("device-refresh")
        }
        assertEquals(AutoPairState.Paired, f.manager.onSellerLogin("seller-1"))
        coVerify(exactly = 2) { f.api.createPairingCode() }
        coVerify(exactly = 2) { f.repository.pair(any()) }
    }

    @Test fun networkFailureUsesBoundedThreeAttempts() = runTest {
        val f = fixture()
        coEvery { f.api.createPairingCode() } throws IOException("offline")
        val result = f.manager.onSellerLogin("seller-1")
        assertTrue(result is AutoPairState.Failed && result.retryable)
        coVerify(exactly = 3) { f.api.createPairingCode() }
    }

    @Test fun concurrentTriggersCreateOnlyOneDevicePair() = runTest {
        val f = fixture()
        coEvery { f.repository.pair("12345678") } coAnswers {
            f.access.set("device-access"); f.refresh.set("device-refresh")
        }
        val a = async { f.manager.ensurePaired() }
        val b = async { f.manager.ensurePaired() }
        assertEquals(AutoPairState.Paired, a.await())
        assertEquals(AutoPairState.Paired, b.await())
        coVerify(exactly = 1) { f.api.createPairingCode() }
        coVerify(exactly = 1) { f.repository.pair(any()) }
    }

    @Test fun sellerSwitchUnpairsOldDeviceBeforeNewPair() = runTest {
        val f = fixture(access = "old-access", refresh = "old-refresh", owner = "seller-old")
        coEvery { f.repository.unpair() } coAnswers { f.access.set(null); f.refresh.set(null) }
        coEvery { f.repository.pair("12345678") } coAnswers {
            f.access.set("new-access"); f.refresh.set("new-refresh")
        }
        assertEquals(AutoPairState.Paired, f.manager.onSellerLogin("seller-new"))
        coVerifyOrder {
            f.repository.unpair()
            f.api.createPairingCode()
            f.repository.pair("12345678")
        }
        verify(exactly = 1) { f.pairedSellerStore.clear() }
        verify(exactly = 1) { f.pairedSellerStore.save("seller-new") }
    }

    @Test fun bbnUnauthorizedDoesNotRetryPairing() = runTest {
        val f = fixture()
        coEvery { f.api.createPairingCode() } throws BbnHttpException(401, "unauthorized")
        val result = f.manager.onSellerLogin("seller-1")
        assertTrue(result is AutoPairState.Failed && !result.retryable)
        coVerify(exactly = 1) { f.api.createPairingCode() }
        coVerify(exactly = 0) { f.repository.pair(any()) }
    }

    @Test fun rePairLoopGuardAllowsThreeInFiveMinutes() {
        val guard = AutoPairLoopGuard(windowMs = 300_000, maxAttempts = 3)
        assertTrue(guard.allow(1_000))
        assertTrue(guard.allow(2_000))
        assertTrue(guard.allow(3_000))
        assertFalse(guard.allow(4_000))
        assertTrue(guard.allow(301_000))
    }

    private fun fixture(
        access: String? = null,
        refresh: String? = null,
        owner: String? = null,
        codes: List<String> = listOf("12345678")
    ): Fixture {
        val api = mockk<BbnApi>()
        val repository = mockk<PrinterRepository>(relaxed = true)
        val tokenStore = mockk<TokenStore>(relaxed = true)
        val sellerSession = mockk<SellerSessionStore>()
        val identity = mockk<SellerIdentityStore>()
        val pairedSellerStore = mockk<PairedSellerStore>(relaxed = true)
        val session = mockk<SessionManager>()
        val loggedIn = MutableStateFlow(true)
        val currentAccess = AtomicReference(access)
        val currentRefresh = AtomicReference(refresh)
        every { tokenStore.accessToken() } answers { currentAccess.get() }
        every { tokenStore.refreshToken() } answers { currentRefresh.get() }
        every { tokenStore.clear() } answers { currentAccess.set(null); currentRefresh.set(null) }
        every { tokenStore.save(any(), any(), any()) } answers { currentAccess.set(firstArg()); currentRefresh.set(secondArg()) }
        every { sellerSession.token() } returns "seller-jwt"
        every { identity.id() } returns "seller-1"
        every { pairedSellerStore.sellerKey() } returns owner
        every { session.isLoggedIn } returns loggedIn
        var index = 0
        coEvery { api.createPairingCode() } coAnswers {
            val code = codes[index.coerceAtMost(codes.lastIndex)]
            index++
            PairingCodeResponse(success = true, code = code)
        }
        coEvery { repository.syncPrinters() } returns Unit
        return Fixture(api, repository, tokenStore, pairedSellerStore, currentAccess, currentRefresh, AutoPairManager(api, repository, tokenStore, sellerSession, identity, pairedSellerStore, session))
    }

    private data class Fixture(
        val api: BbnApi,
        val repository: PrinterRepository,
        val tokenStore: TokenStore,
        val pairedSellerStore: PairedSellerStore,
        val access: AtomicReference<String?>,
        val refresh: AtomicReference<String?>,
        val manager: AutoPairManager,
    )
}
