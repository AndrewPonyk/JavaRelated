package com.example.a1_android_banking_app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.a1_android_banking_app.domain.model.Direction
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransactionStatus

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("accountId"), Index(value = ["accountId", "timestamp"])],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val payeeName: String,
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val status: String,
    val reference: String,
    val fraudScore: Double,
    val timestamp: Long,
) {
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
        timestamp = timestamp,
    )
}
