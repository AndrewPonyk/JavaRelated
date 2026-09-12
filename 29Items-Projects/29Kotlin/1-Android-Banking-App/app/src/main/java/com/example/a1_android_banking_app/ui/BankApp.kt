package com.example.a1_android_banking_app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.a1_android_banking_app.ui.accounts.AccountsScreen
import com.example.a1_android_banking_app.ui.login.LoginScreen
import com.example.a1_android_banking_app.ui.statements.StatementsScreen
import com.example.a1_android_banking_app.ui.transfer.TransferScreen

/**
 * Compose navigation root. Login is the start destination (PROJECT-PLAN Phase 1);
 * TODO(Phase 2): real JWT auth + per-user account scoping on the backend side.
 */
@Composable
fun BankApp() {
    // Minimal Material3 theme until a branded BankAppTheme lands (Phase 1 polish).
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            // Edge-to-edge: keep content clear of status bar, nav bar, cutout and IME.
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onAuthenticated = {
                        navController.navigate(Routes.ACCOUNTS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onTransferClick = { navController.navigate(Routes.TRANSFER) },
                    onAccountClick = { accountId ->
                        navController.navigate(Routes.statements(accountId))
                    },
                )
            }
            composable(Routes.TRANSFER) {
                TransferScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.STATEMENTS) {
                StatementsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val ACCOUNTS = "accounts"
    const val TRANSFER = "transfer"
    const val STATEMENTS = "statements/{accountId}"

    fun statements(accountId: String) = "statements/$accountId"
}
