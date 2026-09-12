package com.example.a1_android_banking_app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a1_android_banking_app.data.repository.BankRepository
import com.example.a1_android_banking_app.domain.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Offline-first UI state: the cached accounts are always renderable (empty on first run),
 * and a failed network refresh degrades to [refreshError] instead of replacing the screen.
 */
data class AccountsUiState(
    val isLoading: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val refreshError: String? = null,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: BankRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState(isLoading = true))
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init {
        // Cache-first: Room is the source of truth; the network refresh only writes into it.
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                _state.update { it.copy(accounts = accounts) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, refreshError = null) }
            repository.refreshAccounts()
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
}
