package com.example.a1_android_banking_app.ui.statements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a1_android_banking_app.data.repository.BankRepository
import com.example.a1_android_banking_app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Same offline-first shape as AccountsUiState: cached rows always renderable. */
data class StatementsUiState(
    val accountId: String,
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val refreshError: String? = null,
)

@HiltViewModel
class StatementsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BankRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle[ARG_ACCOUNT_ID]) {
        "Statements route must carry an accountId argument"
    }

    private val _state = MutableStateFlow(StatementsUiState(accountId = accountId))
    val state: StateFlow<StatementsUiState> = _state.asStateFlow()

    init {
        // Cache-first: Room is the source of truth; the network refresh writes back into it.
        viewModelScope.launch {
            repository.observeTransactions(accountId).collect { transactions ->
                _state.update { it.copy(transactions = transactions) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, refreshError = null) }
            repository.refreshTransactions(accountId)
                .onSuccess { _state.update { it.copy(isLoading = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            refreshError = error.message ?: "Unexpected network error",
                        )
                    }
                }
        }
    }

    companion object {
        const val ARG_ACCOUNT_ID = "accountId"
    }
}
