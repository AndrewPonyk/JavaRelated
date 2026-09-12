package com.example.a1_android_banking_app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.a1_android_banking_app.data.local.dao.AccountDao
import com.example.a1_android_banking_app.data.local.dao.TransactionDao
import com.example.a1_android_banking_app.data.local.entity.AccountEntity
import com.example.a1_android_banking_app.data.local.entity.TransactionEntity

@Database(
    entities = [AccountEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BankDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
}
