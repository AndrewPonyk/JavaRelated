package com.example.a1_android_banking_app.ui.login

import com.example.a1_android_banking_app.security.TokenStore
import com.example.a1_android_banking_app.util.MainDispatcherRule
import com.example.a1_android_banking_app.util.MutableClock
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = MutableClock(Instant.parse("2026-01-15T10:00:00Z").toEpochMilli())
    private val tokenStore = mockk<TokenStore>(relaxed = true)

    private fun viewModel() = LoginViewModel(tokenStore = tokenStore, clock = clock)

    private fun enterPin(vm: LoginViewModel, pin: String) {
        pin.forEach { vm.onDigit(it) }
    }

    @Test
    fun `correct PIN authenticates`() = runTest {
        val vm = viewModel()
        enterPin(vm, LoginViewModel.DEMO_PIN)
        advanceUntilIdle()

        assertTrue(vm.state.value.authenticated)
    }

    @Test
    fun `wrong PIN keeps user locked out of accounts and counts the attempt`() = runTest {
        val vm = viewModel()
        enterPin(vm, "0000")
        advanceUntilIdle()

        assertFalse(vm.state.value.authenticated)
        assertEquals(1, vm.state.value.failedAttempts)
        assertEquals(0, vm.state.value.enteredDigits)
        assertTrue(vm.state.value.error!!.contains("attempt"))
    }

    @Test
    fun `three wrong attempts freeze the pad for the lockout period`() = runTest {
        val vm = viewModel()
        repeat(3) {
            enterPin(vm, "0000")
            advanceUntilIdle()
        }

        assertEquals(LoginViewModel.LOCKOUT_SECONDS, vm.state.value.lockedRemainingSeconds)
        assertEquals(3, vm.state.value.failedAttempts)

        // Digits are ignored while locked.
        enterPin(vm, "1234")
        assertEquals(0, vm.state.value.enteredDigits)
        assertFalse(vm.state.value.authenticated)
    }

    @Test
    fun `lockout expires when the clock passes the deadline`() = runTest {
        val vm = viewModel()
        repeat(3) { enterPin(vm, "0000") }
        advanceUntilIdle()

        clock.advanceMs(10_000)
        vm.tick()
        assertEquals(20, vm.state.value.lockedRemainingSeconds)

        clock.advanceMs(20_000)
        vm.tick()
        assertEquals(0, vm.state.value.lockedRemainingSeconds)
        assertEquals(0, vm.state.value.failedAttempts)

        // The pad accepts the correct PIN again after the lockout lifts.
        enterPin(vm, LoginViewModel.DEMO_PIN)
        advanceUntilIdle()
        assertTrue(vm.state.value.authenticated)
    }

    @Test
    fun `backspace removes digits`() = runTest {
        val vm = viewModel()
        vm.onDigit('1')
        vm.onDigit('2')
        assertEquals(2, vm.state.value.enteredDigits)
        vm.onBackspace()
        assertEquals(1, vm.state.value.enteredDigits)
    }

    @Test
    fun `biometric success authenticates without a PIN`() = runTest {
        val vm = viewModel()
        vm.onBiometricSuccess()
        advanceUntilIdle()

        assertTrue(vm.state.value.authenticated)
    }

    @Test
    fun `biometric error surfaces a message`() = runTest {
        val vm = viewModel()
        vm.onBiometricError("Sensor failed")
        assertEquals("Sensor failed", vm.state.value.error)
    }
}
