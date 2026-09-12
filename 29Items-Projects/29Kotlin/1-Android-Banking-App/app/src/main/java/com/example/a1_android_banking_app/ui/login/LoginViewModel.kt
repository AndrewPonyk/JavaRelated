package com.example.a1_android_banking_app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a1_android_banking_app.security.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

data class LoginUiState(
    /** Digits entered so far, 0..[LoginViewModel.PIN_LENGTH]. */
    val enteredDigits: Int = 0,
    val error: String? = null,
    val failedAttempts: Int = 0,
    val lockedRemainingSeconds: Int = 0,
    val authenticated: Boolean = false,
)

/**
 * Local authentication gate: demo PIN + biometric bypass (PROJECT-PLAN Phase 1).
 *
 * On success a demo session token is written to [TokenStore] — the JWT the backend
 * will issue in Phase 2 lands in the exact same slot (ARCHITECTURE.md 2.5).
 *
 * The PIN is a demo constant; server-side auth is deliberately Phase 2 (PROJECT-PLAN
 * non-goals). Lockout: 3 wrong attempts freeze the pad for [LOCKOUT_SECONDS].
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val pinBuffer = StringBuilder()
    private var lockedUntilMs: Long? = null

    fun onDigit(digit: Char) {
        if (digit !in '0'..'9') return
        updateIfUnlocked {
            if (pinBuffer.length >= PIN_LENGTH) return
            pinBuffer.append(digit)
            _state.update { it.copy(enteredDigits = pinBuffer.length, error = null) }
            if (pinBuffer.length == PIN_LENGTH) verifyPin()
        }
    }

    fun onBackspace() {
        updateIfUnlocked {
            if (pinBuffer.isEmpty()) return
            pinBuffer.deleteCharAt(pinBuffer.length - 1)
            _state.update { it.copy(enteredDigits = pinBuffer.length) }
        }
    }

    fun onBiometricSuccess() {
        authenticate()
    }

    fun onBiometricError(message: String) {
        _state.update { it.copy(error = message) }
    }

    /** Called by the screen's countdown effect once per second while locked. */
    fun tick() {
        val until = lockedUntilMs ?: return
        val remaining = ((until - clock.millis()) / 1000).toInt().coerceAtLeast(0)
        if (remaining == 0) {
            lockedUntilMs = null
            pinBuffer.clear()
            _state.update {
                it.copy(
                    enteredDigits = 0,
                    failedAttempts = 0,
                    lockedRemainingSeconds = 0,
                    error = null,
                )
            }
        } else {
            _state.update { it.copy(lockedRemainingSeconds = remaining) }
        }
    }

    private inline fun updateIfUnlocked(block: () -> Unit) {
        if (lockedUntilMs == null && !_state.value.authenticated) block()
    }

    private fun verifyPin() {
        if (pinBuffer.toString() == DEMO_PIN) {
            authenticate()
            return
        }
        val attempts = _state.value.failedAttempts + 1
        pinBuffer.clear()
        if (attempts >= MAX_ATTEMPTS) {
            lockedUntilMs = clock.millis() + LOCKOUT_SECONDS * 1000L
            _state.update {
                it.copy(
                    enteredDigits = 0,
                    failedAttempts = attempts,
                    lockedRemainingSeconds = LOCKOUT_SECONDS,
                    error = null,
                )
            }
        } else {
            _state.update {
                it.copy(
                    enteredDigits = 0,
                    failedAttempts = attempts,
                    error = "Wrong PIN — ${MAX_ATTEMPTS - attempts} attempt(s) left",
                )
            }
        }
    }

    private fun authenticate() {
        _state.update { it.copy(authenticated = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            // Demo session token; Phase 2 swaps in the backend's real access/refresh pair.
            tokenStore.saveTokens(accessToken = UUID.randomUUID().toString(), refreshToken = "")
        }
    }

    companion object {
        const val PIN_LENGTH = 4
        const val DEMO_PIN = "1234"
        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_SECONDS = 30
    }
}
