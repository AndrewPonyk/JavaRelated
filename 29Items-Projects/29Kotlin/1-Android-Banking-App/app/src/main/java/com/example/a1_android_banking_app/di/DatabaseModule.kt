package com.example.a1_android_banking_app.di

import android.content.Context
import androidx.room.Room
import com.example.a1_android_banking_app.data.local.BankDatabase
import com.example.a1_android_banking_app.data.local.dao.AccountDao
import com.example.a1_android_banking_app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BankDatabase =
        Room.databaseBuilder(context, BankDatabase::class.java, "bank.db")
            // TODO(Phase 2): write real migrations and remove destructive fallback.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAccountDao(database: BankDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideTransactionDao(database: BankDatabase): TransactionDao = database.transactionDao()
}
