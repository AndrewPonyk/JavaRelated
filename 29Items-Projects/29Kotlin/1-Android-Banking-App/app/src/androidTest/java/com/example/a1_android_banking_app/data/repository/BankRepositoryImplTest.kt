package com.example.a1_android_banking_app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.a1_android_banking_app.data.local.BankDatabase
import com.example.a1_android_banking_app.data.remote.BankingApi
import com.example.a1_android_banking_app.domain.model.TransferRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

/**
 * TECH-NOTES 3.2 "Repository" row: real Room (in-memory) + real Retrofit against MockWebServer —
 * verifies the offline-first write-back path (network response lands in Room and re-emits).
 */
@RunWith(AndroidJUnit4::class)
class BankRepositoryImplTest {

    private lateinit var database: BankDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: BankRepositoryImpl

    private val accountJson = """
        [{"id":"acc-1","name":"Main account","iban":"UA90","balanceMinor":1245075,"currency":"EUR","type":"CHECKING"}]
    """.trimIndent()

    private val transactionJson = """
        {"id":"tx-1","accountId":"acc-1","payeeName":"Jane Doe","amountMinor":1250,"currency":"EUR",
         "direction":"DEBIT","status":"COMPLETED","reference":"Rent","fraudScore":0.0,"createdAt":1770000000000}
    """.trimIndent()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, BankDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<BankingApi>()
        repository = BankRepositoryImpl(api, database.accountDao(), database.transactionDao())
    }

    /** Transactions carry a FK to accounts — the parent row must exist before any upsert. */
    private suspend fun seedAccount() {
        database.accountDao().upsertAll(
            listOf(
                com.example.a1_android_banking_app.data.local.entity.AccountEntity(
                    id = "acc-1", name = "Main account", iban = "UA90",
                    balanceMinor = 1_245_075, currency = "EUR", type = "CHECKING",
                )
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun refreshAccountsWritesNetworkResponseIntoRoom() = runBlocking {
        server.enqueue(
            MockResponse().setBody(accountJson).addHeader("Content-Type", "application/json")
        )

        val result = repository.refreshAccounts()

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/accounts", server.takeRequest().path)
        val accounts = repository.observeAccounts().first()
        assertEquals(1, accounts.size)
        assertEquals("acc-1", accounts.single().id)
        assertEquals(1_245_075L, accounts.single().balanceMinor)
    }

    @Test
    fun refreshAccountsFailureSurfacesAsResultFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.refreshAccounts()

        assertTrue(result.isFailure)
        assertTrue(repository.observeAccounts().first().isEmpty())
    }

    @Test
    fun submitTransferPostsIdempotencyKeyAndUpsertsTransactionIntoRoom() = runBlocking {
        seedAccount()
        server.enqueue(
            MockResponse().setBody(transactionJson).addHeader("Content-Type", "application/json")
        )
        val request = TransferRequest(
            fromAccountId = "acc-1",
            payeeName = "Jane Doe",
            payeeIban = "UA90",
            amountMinor = 1_250,
            currency = "EUR",
            reference = "Rent",
            idempotencyKey = "key-42",
        )

        val result = repository.submitTransfer(request)

        assertTrue(result.isSuccess)
        assertEquals("tx-1", result.getOrThrow().id)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/payments", recorded.path)
        assertEquals("key-42", recorded.getHeader("Idempotency-Key"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"fromAccountId\":\"acc-1\""))
        assertTrue(body.contains("\"amountMinor\":1250"))

        // The returned transaction is cached for the statements screen.
        val cached = repository.observeTransactions("acc-1").first()
        assertEquals(1, cached.size)
        assertEquals("tx-1", cached.single().id)
    }

    @Test
    fun refreshTransactionsWritesNetworkResponseIntoRoom() = runBlocking {
        seedAccount()
        server.enqueue(
            MockResponse()
                .setBody("[$transactionJson]")
                .addHeader("Content-Type", "application/json")
        )

        val result = repository.refreshTransactions("acc-1")

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/accounts/acc-1/transactions?limit=50", server.takeRequest().path)
        val cached = repository.observeTransactions("acc-1").first()
        assertEquals(1, cached.size)
    }
}
