package com.example.a1_android_banking_app.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a1_android_banking_app.data.repository.BankRepository
import com.example.a1_android_banking_app.domain.model.Account
import com.example.a1_android_banking_app.domain.model.FraudRisk
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransferRequest
import com.example.a1_android_banking_app.domain.model.parseAmountToMinor
import com.example.a1_android_banking_app.fraud.FraudDetectionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferFormState(
    val payeeName: String = "",
    val payeeIban: String = "",
    val amountInput: String = "",
    val reference: String = "",
)

sealed interface TransferUiState {
    data object Idle : TransferUiState
    data object Submitting : TransferUiState
    data class Blocked(val reasons: List<String>, val request: TransferRequest) : TransferUiState
    data class Success(val transaction: Transaction) : TransferUiState
    data class Failed(val message: String) : TransferUiState
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val repository: BankRepository,
    private val fraudEngine: FraudDetectionEngine,
) : ViewModel() {

    private val _form = MutableStateFlow(TransferFormState())
    val form: StateFlow<TransferFormState> = _form.asStateFlow()

    private val _state = MutableStateFlow<TransferUiState>(TransferUiState.Idle)
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    /** Cached accounts for the picker (Room flow — works offline). */
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    private val _selectedAccount = MutableStateFlow<Account?>(null)

    /** The picked account, or the first cached one until the user chooses. */
    val selectedAccount: StateFlow<Account?> = _selectedAccount.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAccounts().collect { _accounts.value = it }
        }
        viewModelScope.launch {
            combine(repository.observeAccounts(), _selectedAccountId) { all, selected ->
                all.firstOrNull { it.id == selected } ?: all.firstOrNull()
            }.collect { _selectedAccount.value = it }
        }
    }

    fun selectAccount(accountId: String) {
        _selectedAccountId.value = accountId
    }

    fun updatePayeeName(value: String) = _form.update { it.copy(payeeName = value) }
    fun updatePayeeIban(value: String) = _form.update { it.copy(payeeIban = value) }
    fun updateAmount(value: String) = _form.update {
        it.copy(amountInput = value.filter { c -> c.isDigit() || c == '.' || c == ',' })
    }

    fun updateReference(value: String) = _form.update { it.copy(reference = value) }

    val amountMinor: Long?
        get() = parseAmountToMinor(_form.value.amountInput)

    /** Non-blank but unparseable amount — drives the field's error state. */
    val amountInvalid: Boolean
        get() = _form.value.amountInput.isNotBlank() && amountMinor == null

    val exceedsBalance: Boolean
        get() {
            val account = selectedAccount.value ?: return false
            val minor = amountMinor ?: return false
            return minor > account.balanceMinor
        }

    val canSubmit: Boolean
        get() = _form.value.payeeName.isNotBlank() &&
            _form.value.payeeIban.isNotBlank() &&
            amountMinor != null &&
            !exceedsBalance

    fun reset() {
        if (_state.value is TransferUiState.Blocked) _state.value = TransferUiState.Idle
    }

    fun submit() {
        val account = selectedAccount.value
        val minor = amountMinor
        if (account == null || minor == null) {
            _state.value = TransferUiState.Failed("Pick an account and enter a valid amount")
            return
        }
        if (minor > account.balanceMinor) {
            // Client-side pre-check; the server balance check remains authoritative.
            _state.value = TransferUiState.Failed("Insufficient funds — balance is ${account.formattedBalance}")
            return
        }
        val request = TransferRequest(
            fromAccountId = account.id,
            payeeName = _form.value.payeeName.trim(),
            payeeIban = _form.value.payeeIban.replace(" ", ""),
            amountMinor = minor,
            currency = account.currency,
            reference = _form.value.reference.trim(),
        )
        viewModelScope.launch {
            _state.value = TransferUiState.Submitting
            val recent = repository.observeTransactions(request.fromAccountId).first()
            val assessment = fraudEngine.assess(request, recent)
            if (assessment.risk == FraudRisk.HIGH) {
                // UX-level gate: user may override; the server engine still gets the final word.
                _state.value = TransferUiState.Blocked(assessment.reasons, request)
            } else {
                performSubmit(request)
            }
        }
    }

    fun confirmBlocked() {
        val blocked = _state.value as? TransferUiState.Blocked ?: return
        viewModelScope.launch {
            _state.value = TransferUiState.Submitting
            performSubmit(blocked.request)
        }
    }

    private suspend fun performSubmit(request: TransferRequest) {
        repository.submitTransfer(request)
            .onSuccess { transaction ->
                // Best-effort balance sync: submitTransfer upserts only the transaction —
                // without this the accounts screen keeps the pre-transfer balance in Room.
                repository.refreshAccounts()
                _state.value = TransferUiState.Success(transaction)
            }
            .onFailure { _state.value = TransferUiState.Failed(it.message ?: "Transfer failed") }
    }
}
