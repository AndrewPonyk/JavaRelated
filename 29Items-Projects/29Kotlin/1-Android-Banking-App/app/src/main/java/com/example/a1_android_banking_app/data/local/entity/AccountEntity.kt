package com.example.a1_android_banking_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.a1_android_banking_app.domain.model.Account

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iban: String,
    val balanceMinor: Long,
    val currency: String,
    val type: String,
) {
    fun toDomain() = Account(
        id = id,
        name = name,
        iban = iban,
        balanceMinor = balanceMinor,
        currency = currency,
        type = type,
    )
}
