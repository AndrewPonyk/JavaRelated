package com.example.a1_android_banking_app.util

import com.example.a1_android_banking_app.data.repository.BankRepository
import com.example.a1_android_banking_app.domain.model.Account
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransferRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Scriptable in-memory BankRepository — no Room, no network. */
class FakeBankRepository : BankRepository {

    val accounts = MutableStateFlow<List<Account>>(emptyList())
    private val transactionsByAccount = mutableMapOf<String, MutableStateFlow<List<Transaction>>>()

    var refreshAccountsResult: Result<Unit> = Result.success(Unit)
    var refreshTransactionsResult: Result<Unit> = Result.success(Unit)
    var submitTransferResult: Result<Transaction> =
        Result.failure(IllegalStateException("submitTransferResult not scripted"))

    var refreshAccountsCalls: Int = 0
        private set

    val submittedRequests = mutableListOf<TransferRequest>()

    override fun observeAccounts(): Flow<List<Account>> = accounts

    override fun observeTransactions(accountId: String): Flow<List<Transaction>> =
        transactionsByAccount.getOrPut(accountId) { MutableStateFlow(emptyList()) }

    fun setTransactions(accountId: String, transactions: List<Transaction>) {
        transactionsByAccount.getOrPut(accountId) { MutableStateFlow(emptyList()) }.value = transactions
    }

    override suspend fun refreshAccounts(): Result<Unit> {
        refreshAccountsCalls++
        return refreshAccountsResult
    }

    override suspend fun refreshTransactions(accountId: String): Result<Unit> =
        refreshTransactionsResult

    override suspend fun submitTransfer(request: TransferRequest): Result<Transaction> {
        submittedRequests += request
        return submitTransferResult
    }
}
