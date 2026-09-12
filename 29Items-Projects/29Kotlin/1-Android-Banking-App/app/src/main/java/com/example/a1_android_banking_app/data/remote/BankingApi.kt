package com.example.a1_android_banking_app.data.remote

import com.example.a1_android_banking_app.data.remote.dto.AccountSummary
import com.example.a1_android_banking_app.data.remote.dto.TransactionDto
import com.example.a1_android_banking_app.data.remote.dto.TransferRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST contract with the Ktor backend. Paths mirror backend/src/.../api/PaymentsRoute.kt.
 * TODO(Phase 2): add auth header interceptor (TokenStore) instead of per-call headers.
 */
interface BankingApi {

    @GET("api/v1/accounts")
    suspend fun accounts(): List<AccountSummary>

    @GET("api/v1/accounts/{id}/transactions")
    suspend fun transactions(
        @Path("id") accountId: String,
        @Query("limit") limit: Int = 50,
    ): List<TransactionDto>

    @POST("api/v1/payments")
    suspend fun createPayment(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: TransferRequestDto,
    ): TransactionDto
}
