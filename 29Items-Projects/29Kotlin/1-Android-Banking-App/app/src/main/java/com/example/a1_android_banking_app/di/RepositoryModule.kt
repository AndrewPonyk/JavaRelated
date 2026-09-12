package com.example.a1_android_banking_app.di

import com.example.a1_android_banking_app.data.repository.BankRepository
import com.example.a1_android_banking_app.data.repository.BankRepositoryImpl
import com.example.a1_android_banking_app.fraud.FraudDetectionEngine
import com.example.a1_android_banking_app.fraud.RuleBasedFraudDetectionEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    companion object {
        /** Single injectable Clock — deterministic time for engines and lockouts (TECH-NOTES 3.6 #10). */
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }

    @Binds
    @Singleton
    abstract fun bindBankRepository(impl: BankRepositoryImpl): BankRepository

    @Binds
    @Singleton
    abstract fun bindFraudDetectionEngine(impl: RuleBasedFraudDetectionEngine): FraudDetectionEngine
}
