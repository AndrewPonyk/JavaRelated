package com.example.a1_android_banking_app.ui.transfer

import com.example.a1_android_banking_app.domain.model.Account
import com.example.a1_android_banking_app.domain.model.Direction
import com.example.a1_android_banking_app.domain.model.FraudAssessment
import com.example.a1_android_banking_app.domain.model.FraudRisk
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransactionStatus
import com.example.a1_android_banking_app.fraud.FraudDetectionEngine
import com.example.a1_android_banking_app.util.FakeBankRepository
import com.example.a1_android_banking_app.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TransferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBankRepository()
    private val fraudEngine = FakeFraudEngine(FraudAssessment(score = 0.0, risk = FraudRisk.LOW))

    private val mainAccount = Account(
        id = "acc-1", name = "Main account", iban = "UA90", balanceMinor = 1_245_075,
        currency = "EUR", type = "CHECKING",
    )
    private val savingsAccount = Account(
        id = "acc-2", name = "Savings", iban = "UA91", balanceMinor = 45_000_000,
        currency = "EUR", type = "SAVINGS",
    )

    private fun viewModel() = TransferViewModel(repository, fraudEngine)

    private fun fillForm(vm: TransferViewModel, amount: String = "12.50") {
        vm.updatePayeeName("Jane Doe")
        vm.updatePayeeIban("UA90 3052 2999 9900 1111 2233 4455 1")
        vm.updateAmount(amount)
        vm.updateReference("Rent")
    }

    // ---- Form validation ----

    @Test
    fun `amount field accepts comma notation and converts to minor units`() = runTest {
        val vm = viewModel()
        vm.updateAmount("12,5")
        assertEquals(1250L, vm.amountMinor)
        assertFalse(vm.amountInvalid)
    }

    @Test
    fun `unparseable non-blank amount is invalid`() = runTest {
        val vm = viewModel()
        vm.updateAmount("1.2.3")
        assertNull(vm.amountMinor)
        assertTrue(vm.amountInvalid)
        assertFalse(vm.canSubmit)
    }

    @Test
    fun `cannot submit without payee and IBAN`() = runTest {
        val vm = viewModel()
        repository.accounts.value = listOf(mainAccount)
        advanceUntilIdle()

        vm.updateAmount("10.00")
        assertFalse(vm.canSubmit)

        fillForm(vm)
        assertTrue(vm.canSubmit)
    }

    // ---- Account picker ----

    @Test
    fun `first cached account is selected by default`() = runTest {
        repository.accounts.value = listOf(savingsAccount, mainAccount)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(savingsAccount.id, vm.selectedAccount.value?.id)
    }

    @Test
    fun `explicit selection wins over default order`() = runTest {
        repository.accounts.value = listOf(savingsAccount, mainAccount)
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectAccount(mainAccount.id)
        advanceUntilIdle()

        assertEquals(mainAccount.id, vm.selectedAccount.value?.id)
    }

    // ---- Balance checks ----

    @Test
    fun `amount above balance blocks submit client-side`() = runTest {
        repository.accounts.value = listOf(mainAccount) // 12,450.75
        val vm = viewModel()
        advanceUntilIdle()

        fillForm(vm, amount = "99999.00")
        assertTrue(vm.exceedsBalance)
        assertFalse(vm.canSubmit)

        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.state.value is TransferUiState.Failed)
        assertTrue((vm.state.value as TransferUiState.Failed).message.contains("Insufficient funds"))
        assertTrue(repository.submittedRequests.isEmpty()) // never hit the network
    }

    // ---- Submit flows ----

    private val completedTx = Transaction(
        id = "tx-1", accountId = "acc-1", payeeName = "Jane Doe",
        amountMinor = 1_250, currency = "EUR", direction = Direction.DEBIT,
        status = TransactionStatus.COMPLETED, reference = "Rent", fraudScore = 0.0,
        timestamp = 1_000L,
    )

    @Test
    fun `low-risk transfer submits against the selected account`() = runTest {
        repository.accounts.value = listOf(mainAccount)
        repository.submitTransferResult = Result.success(completedTx)
        val vm = viewModel()
        advanceUntilIdle()

        fillForm(vm)
        vm.submit()
        advanceUntilIdle()

        val success = vm.state.value as TransferUiState.Success
        assertEquals("tx-1", success.transaction.id)

        // The stale-balance regression: a successful transfer must sync accounts back into Room.
        assertEquals(1, repository.refreshAccountsCalls)

        val request = repository.submittedRequests.single()
        assertEquals("acc-1", request.fromAccountId)
        assertEquals(1250L, request.amountMinor)
        assertEquals("EUR", request.currency)
        assertEquals("UA903052299999001111223344551", request.payeeIban) // spaces stripped
    }

    @Test
    fun `high-risk transfer is blocked until the user confirms`() = runTest {
        repository.accounts.value = listOf(mainAccount)
        fraudEngine.assessment = FraudAssessment(
            score = 0.75, risk = FraudRisk.HIGH,
            reasons = listOf("Amount exceeds the single-transfer limit"),
        )
        repository.submitTransferResult = Result.success(completedTx)
        val vm = viewModel()
        advanceUntilIdle()

        fillForm(vm)
        vm.submit()
        advanceUntilIdle()

        val blocked = vm.state.value as TransferUiState.Blocked
        assertEquals(listOf("Amount exceeds the single-transfer limit"), blocked.reasons)
        assertTrue(repository.submittedRequests.isEmpty()) // gate: nothing sent yet

        vm.confirmBlocked()
        advanceUntilIdle()

        assertTrue(vm.state.value is TransferUiState.Success)
        assertEquals(1, repository.submittedRequests.size)
    }

    @Test
    fun `network failure folds into Failed state`() = runTest {
        repository.accounts.value = listOf(mainAccount)
        repository.submitTransferResult = Result.failure(java.io.IOException("Connection reset"))
        val vm = viewModel()
        advanceUntilIdle()

        fillForm(vm)
        vm.submit()
        advanceUntilIdle()

        val failed = vm.state.value as TransferUiState.Failed
        assertTrue(failed.message.contains("Connection reset"))
    }

    @Test
    fun `server fraud rejection folds into Failed state`() = runTest {
        repository.accounts.value = listOf(mainAccount)
        repository.submitTransferResult = Result.failure(
            retrofit2.HttpException(
                retrofit2.Response.error<Unit>(
                    403,
                    "{\"message\":\"Blocked\"}".toResponseBody(null),
                )
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        fillForm(vm)
        vm.submit()
        advanceUntilIdle()

        assertTrue(vm.state.value is TransferUiState.Failed)
    }

    @Test
    fun `reset returns from Blocked to Idle`() = runTest {
        repository.accounts.value = listOf(mainAccount)
        fraudEngine.assessment = FraudAssessment(score = 0.9, risk = FraudRisk.HIGH, reasons = listOf("r"))
        val vm = viewModel()
        advanceUntilIdle()

        fillForm(vm)
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value is TransferUiState.Blocked)

        vm.reset()
        assertEquals(TransferUiState.Idle, vm.state.value)
    }
}

/** Scriptable engine stand-in. */
private class FakeFraudEngine(
    var assessment: FraudAssessment,
) : FraudDetectionEngine {
    override fun assess(
        request: com.example.a1_android_banking_app.domain.model.TransferRequest,
        recent: List<Transaction>,
    ) = assessment
}
