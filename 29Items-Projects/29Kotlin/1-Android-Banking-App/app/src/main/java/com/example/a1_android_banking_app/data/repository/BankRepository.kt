package com.example.a1_android_banking_app.data.repository

import com.example.a1_android_banking_app.data.local.dao.AccountDao
import com.example.a1_android_banking_app.data.local.dao.TransactionDao
import com.example.a1_android_banking_app.data.remote.BankingApi
import com.example.a1_android_banking_app.data.remote.dto.toDto
import com.example.a1_android_banking_app.domain.model.Account
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransferRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first facade: the UI observes Room; network refreshes write back into Room.
 * Failures surface as [Result] — ViewModels fold them into UiStates (ARCHITECTURE.md 2.6).
 */
interface BankRepository {
    fun observeAccounts(): Flow<List<Account>>
    fun observeTransactions(accountId: String): Flow<List<Transaction>>
    suspend fun refreshAccounts(): Result<Unit>
    suspend fun refreshTransactions(accountId: String): Result<Unit>
    suspend fun submitTransfer(request: TransferRequest): Result<Transaction>
}

@Singleton
class BankRepositoryImpl @Inject constructor(
    private val api: BankingApi,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
) : BankRepository {

    override fun observeAccounts(): Flow<List<Account>> =
        accountDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeTransactions(accountId: String): Flow<List<Transaction>> =
        transactionDao.observeForAccount(accountId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshAccounts(): Result<Unit> = runCatching {
        // TODO(Phase 2): map HTTP errors to domain exceptions (offline vs auth vs 5xx).
        val remote = api.accounts()
        accountDao.upsertAll(remote.map { it.toEntity() })
    }

    override suspend fun refreshTransactions(accountId: String): Result<Unit> = runCatching {
        val remote = api.transactions(accountId)
        transactionDao.upsertAll(remote.map { it.toEntity() })
    }

    override suspend fun submitTransfer(request: TransferRequest): Result<Transaction> = runCatching {
        val dto = api.createPayment(idempotencyKey = request.idempotencyKey, body = request.toDto())
        transactionDao.upsert(dto.toEntity())
        dto.toDomain()
    }
}
