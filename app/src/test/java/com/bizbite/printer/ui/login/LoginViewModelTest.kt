package com.bizbite.printer.ui.login

import com.bizbite.printer.data.auth.AuthRepository
import com.bizbite.printer.data.auth.AutoPairManager
import com.bizbite.printer.data.auth.AutoPairState
import com.bizbite.printer.data.auth.LoginResult
import com.bizbite.printer.data.remote.BbnHttpException
import com.bizbite.printer.data.remote.BbnUser
import com.bizbite.printer.data.security.SellerIdentityStore
import com.bizbite.printer.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test
    fun phoneInputKeepsLastTenAsciiDigits() {
        assertEquals("9876543210", normalizePhoneInput("+91 98765 43210"))
        assertEquals("1234567890", normalizePhoneInput("1234567890"))
        assertTrue(isValidPhone("9876543210"))
        assertFalse(isValidPhone("919876543210"))
        assertFalse(isValidPhone("987654321"))
    }

    @Test
    fun errorMappingUsesStatusDefaultsAndServerMessage() {
        assertEquals("Invalid phone number or PIN.", loginErrorMessage(BbnHttpException(401, "Unauthorized")))
        assertEquals(
            "Too many attempts. Try again after 15 minutes.",
            loginErrorMessage(BbnHttpException(429, ""))
        )
        assertEquals("Wait a while", loginErrorMessage(BbnHttpException(429, "Wait a while")))
        assertEquals("Please retry", loginErrorMessage(BbnHttpException(500, "Please retry")))
        assertEquals("Server rejected PIN", loginErrorMessage(IllegalStateException("Server rejected PIN")))
        assertEquals("Could not reach BizBiteNow. Check your connection and try again.", loginErrorMessage(java.io.IOException()))
    }

    @Test
    fun successfulLoginMovesToSuccessAndStartsSession() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = mockk<AuthRepository>()
            val session = mockk<SessionManager>(relaxed = true)
            val identity = mockk<SellerIdentityStore>(relaxed = true)
            val pairing = mockk<AutoPairManager>()
            coEvery { pairing.onSellerLogin(any()) } returns AutoPairState.NotPaired
            val pendingResult = CompletableDeferred<LoginResult>()
            coEvery { auth.login("9876543210", "2468") } coAnswers { pendingResult.await() }
            val expectedResult = LoginResult(
                "seller-token", true, "Seller", BbnUser("id", "Shop", "Seller", "9876543210")
            )
            val viewModel = LoginViewModel(auth, session, identity, pairing)
            viewModel.phoneChanged("+91 98765 43210")
            viewModel.pinChanged("24x68")

            assertEquals("9876543210", viewModel.state.value.phone)
            assertEquals("2468", viewModel.state.value.pin)
            viewModel.login()
            runCurrent()
            assertTrue(viewModel.state.value.loading)
            pendingResult.complete(expectedResult)
            advanceUntilIdle()

            assertEquals(LoginStep.SUCCESS, viewModel.state.value.step)
            assertFalse(viewModel.state.value.loading)
            coVerify(exactly = 1) { auth.login("9876543210", "2468") }
            verify(exactly = 1) { session.login("seller-token") }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unacceptedAgreementDoesNotSaveIdentityOrStartSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mockk<AuthRepository>()
            val session = mockk<SessionManager>(relaxed = true)
            val identity = mockk<SellerIdentityStore>(relaxed = true)
            coEvery { auth.login(any(), any()) } returns LoginResult("seller-token", false, "Seller", null)
            val viewModel = LoginViewModel(auth, session, identity, mockk(relaxed = true))
            viewModel.phoneChanged("9876543210")
            viewModel.pinChanged("2468")

            viewModel.login()
            advanceUntilIdle()

            assertEquals(LoginStep.LOGIN, viewModel.state.value.step)
            assertTrue(viewModel.state.value.error!!.contains("agreement"))
            verify(exactly = 0) { session.login(any()) }
            verify(exactly = 0) { identity.save(any(), any(), any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidPhonePreventsNetworkCallAndSetsValidationError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mockk<AuthRepository>()
            val viewModel = LoginViewModel(auth, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            viewModel.phoneChanged("12345")
            viewModel.pinChanged("1")

            viewModel.login()

            assertEquals("Enter a valid 10-digit phone number.", viewModel.state.value.error)
            coVerify(exactly = 0) { auth.login(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun emptyPinPreventsNetworkCallAndSetsValidationError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mockk<AuthRepository>()
            val viewModel = LoginViewModel(auth, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            viewModel.phoneChanged("9876543210")

            viewModel.login()

            assertEquals("Enter your PIN.", viewModel.state.value.error)
            coVerify(exactly = 0) { auth.login(any(), any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
