package com.example.a1_android_banking_app.data.remote.dto

import com.example.a1_android_banking_app.data.local.entity.AccountEntity
import com.example.a1_android_banking_app.data.local.entity.TransactionEntity
import com.example.a1_android_banking_app.domain.model.Account
import com.example.a1_android_banking_app.domain.model.Direction
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransactionStatus
import com.example.a1_android_banking_app.domain.model.TransferRequest
import kotlinx.serialization.Serializable

/** Plain type alias so BankingApi stays readable; kept here with the rest of the wire models. */
typealias AccountSummary = AccountDto

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val iban: String,
    val balanceMinor: Long,
    val currency: String,
    val type: String,
) {
    fun toEntity() = AccountEntity(
        id = id, name = name, iban = iban,
        balanceMinor = balanceMinor, currency = currency, type = type,
    )

    fun toDomain() = Account(
        id = id, name = name, iban = iban,
        balanceMinor = balanceMinor, currency = currency, type = type,
    )
}

@Serializable
data class TransactionDto(
    val id: String,
    val accountId: String,
    val payeeName: String,
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val status: String,
    val reference: String = "",
    val fraudScore: Double = 0.0,
    val createdAt: Long,
) {
    fun toEntity() = TransactionEntity(
        id = id,
        accountId = accountId,
        payeeName = payeeName,
        amountMinor = amountMinor,
        currency = currency,
        direction = direction,
        status = status,
        reference = reference,
        fraudScore = fraudScore,
        timestamp = createdAt,
    )

    fun toDomain() = Transaction(
        id = id,
        accountId = accountId,
        payeeName = payeeName,
        amountMinor = amountMinor,
        currency = currency,
        direction = runCatching { Direction.valueOf(direction) }.getOrDefault(Direction.DEBIT),
        status = runCatching { TransactionStatus.valueOf(status) }.getOrDefault(TransactionStatus.PENDING),
        reference = reference,
        fraudScore = fraudScore,
        timestamp = createdAt,
    )
}

@Serializable
data class TransferRequestDto(
    val fromAccountId: String,
    val payeeName: String,
    val payeeIban: String,
    val amountMinor: Long,
    val currency: String,
    val reference: String,
)

fun TransferRequest.toDto() = TransferRequestDto(
    fromAccountId = fromAccountId,
    payeeName = payeeName,
    payeeIban = payeeIban,
    amountMinor = amountMinor,
    currency = currency,
    reference = reference,
)
