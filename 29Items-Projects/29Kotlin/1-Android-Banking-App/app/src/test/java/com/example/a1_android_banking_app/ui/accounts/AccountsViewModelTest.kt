package com.example.a1_android_banking_app.ui.accounts

import com.example.a1_android_banking_app.domain.model.Account
import com.example.a1_android_banking_app.util.FakeBankRepository
import com.example.a1_android_banking_app.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBankRepository()

    private val account = Account(
        id = "acc-1", name = "Main account", iban = "UA90", balanceMinor = 1_245_075,
        currency = "EUR", type = "CHECKING",
    )

    @Test
    fun `cached accounts are shown even when the network refresh fails`() = runTest {
        repository.accounts.value = listOf(account)
        repository.refreshAccountsResult = Result.failure(java.io.IOException("offline"))

        val vm = AccountsViewModel(repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(account), state.accounts)
        assertNotNull(state.refreshError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `successful refresh clears the previous error`() = runTest {
        repository.accounts.value = listOf(account)
        repository.refreshAccountsResult = Result.failure(java.io.IOException("offline"))
        val vm = AccountsViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.refreshError != null)

        repository.refreshAccountsResult = Result.success(Unit)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(null, vm.state.value.refreshError)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `room updates propagate into the state flow`() = runTest {
        val vm = AccountsViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.state.value.accounts.isEmpty())

        repository.accounts.value = listOf(account)
        advanceUntilIdle()

        assertEquals(listOf(account), vm.state.value.accounts)
    }
}
