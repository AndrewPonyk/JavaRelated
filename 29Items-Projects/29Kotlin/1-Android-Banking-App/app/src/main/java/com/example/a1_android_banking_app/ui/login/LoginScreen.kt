package com.example.a1_android_banking_app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.a1_android_banking_app.biometric.BiometricAuthManager
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onAuthenticated: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val activity = LocalContext.current as? FragmentActivity
    val biometricManager = remember(activity) {
        activity?.let { BiometricAuthManager(it.applicationContext) }
    }
    val biometricAvailable = remember(biometricManager) {
        biometricManager?.canAuthenticate() == true
    }

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) onAuthenticated()
    }
    // Drive the lockout countdown while the pad is frozen.
    LaunchedEffect(state.lockedRemainingSeconds > 0) {
        while (state.lockedRemainingSeconds > 0) {
            delay(1000)
            viewModel.tick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your PIN to access your accounts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        PinDots(filled = state.enteredDigits)
        Spacer(Modifier.height(16.dp))

        val error = state.error
        val message = when {
            state.lockedRemainingSeconds > 0 ->
                "Too many attempts — try again in ${state.lockedRemainingSeconds}s"
            error != null -> error
            else -> ""
        }
        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.lockedRemainingSeconds > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (state.lockedRemainingSeconds == 0) {
            PinPad(
                enabled = !state.authenticated,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
            )
        }

        if (biometricAvailable && state.lockedRemainingSeconds == 0) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val manager = biometricManager
                    val fragActivity = activity
                    if (manager != null && fragActivity != null) {
                        manager.authenticate(
                            activity = fragActivity,
                            onSuccess = viewModel::onBiometricSuccess,
                            onError = viewModel::onBiometricError,
                        )
                    }
                },
            ) { Text("Unlock with biometrics") }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Demo build — PIN is ${LoginViewModel.DEMO_PIN}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(LoginViewModel.PIN_LENGTH) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
            )
        }
    }
}

@Composable
private fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(' ', '0', '<'),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    when (key) {
                        ' ' -> Spacer(Modifier.size(72.dp))
                        '<' -> TextButton(
                            onClick = onBackspace,
                            enabled = enabled,
                            modifier = Modifier.size(72.dp),
                        ) { Text("⌫", style = MaterialTheme.typography.headlineSmall) }
                        else -> KeyButton(key = key, enabled = enabled, onDigit = onDigit)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(key: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    Button(
        onClick = { onDigit(key) },
        enabled = enabled,
        modifier = Modifier.size(72.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(key.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}
