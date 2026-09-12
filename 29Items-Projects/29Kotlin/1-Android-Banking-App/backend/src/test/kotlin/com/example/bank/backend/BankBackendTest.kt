package com.example.bank.backend

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * testApplication auto-loads backend/src/main/resources/application.conf from the test
 * classpath, which starts `module()` exactly once — starting it again here would install
 * every plugin twice (DuplicatePluginException). These tests therefore exercise the real
 * EngineMain wiring, config included.
 */
class BankBackendTest {

    private val validPayment =
        """{"fromAccountId":"acc-1","payeeName":"Jane Doe","payeeIban":"UA213223130000026007233566001","amountMinor":1500,"currency":"EUR"}"""

    private suspend fun io.ktor.client.HttpClient.postPayment(
        idempotencyKey: String,
        fromAccountId: String = "acc-1",
        payeeName: String = "Jane Doe",
        amountMinor: Long = 1_500,
    ): HttpStatusCode = post("/api/v1/payments") {
        header("Idempotency-Key", idempotencyKey)
        contentType(ContentType.Application.Json)
        setBody(
            """{"fromAccountId":"$fromAccountId","payeeName":"$payeeName",""" +
                """"payeeIban":"UA213223130000026007233566001","amountMinor":$amountMinor,"currency":"EUR"}"""
        )
    }.status

    @Test
    fun `health endpoint responds ok`() = testApplication {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `accounts endpoint returns seeded demo data`() = testApplication {
        val response = client.get("/api/v1/accounts")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("acc-1"))
    }

    // ---- Statements endpoint (ARCHITECTURE.md 2.2 contract) ----

    @Test
    fun `statements endpoint returns seeded transactions for a known account`() = testApplication {
        val response = client.get("/api/v1/accounts/acc-1/transactions")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("tx-seed-1"), "expected seed history, got: $body")
    }

    @Test
    fun `statements endpoint returns 404 for unknown account`() = testApplication {
        val response = client.get("/api/v1/accounts/acc-99/transactions")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `statements endpoint rejects non-integer limit with 422`() = testApplication {
        val response = client.get("/api/v1/accounts/acc-1/transactions?limit=abc")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `statements endpoint rejects out-of-range limit with 422`() = testApplication {
        val response = client.get("/api/v1/accounts/acc-1/transactions?limit=1000")
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `statements endpoint honours limit and newest-first order`() = testApplication {
        // Create one live payment — it must come back first (newest), ahead of the older seeds.
        client.postPayment("key-order")
        val response = client.get("/api/v1/accounts/acc-1/transactions?limit=2")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.indexOf("tx-1") < body.indexOf("tx-seed"), "expected newest first, got: $body")
    }

    // ---- Payment validation ----

    @Test
    fun `payment without idempotency key is rejected`() = testApplication {
        val response = client.post("/api/v1/payments") {
            contentType(ContentType.Application.Json)
            setBody(validPayment)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid payment fails validation`() = testApplication {
        val response = client.post("/api/v1/payments") {
            header("Idempotency-Key", "key-invalid")
            contentType(ContentType.Application.Json)
            setBody("""{"fromAccountId":"acc-1","payeeName":"","payeeIban":"","amountMinor":0,"currency":"EUR"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `payment for unknown account is rejected with 422`() = testApplication {
        val status = client.postPayment("key-unknown", fromAccountId = "acc-99")
        assertEquals(HttpStatusCode.UnprocessableEntity, status)
    }

    @Test
    fun `malformed json body is rejected with 422`() = testApplication {
        val response = client.post("/api/v1/payments") {
            header("Idempotency-Key", "key-malformed")
            contentType(ContentType.Application.Json)
            setBody("""{"fromAccountId": "acc-1", not-json""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `request without json content type is rejected with 415`() = testApplication {
        val response = client.post("/api/v1/payments") {
            header("Idempotency-Key", "key-notype")
            setBody(validPayment)
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    // ---- Idempotency ----

    @Test
    fun `valid payment is created and replay returns the same transaction`() = testApplication {
        val first = client.post("/api/v1/payments") {
            header("Idempotency-Key", "key-1")
            contentType(ContentType.Application.Json)
            setBody(validPayment)
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val replay = client.post("/api/v1/payments") {
            header("Idempotency-Key", "key-1")
            contentType(ContentType.Application.Json)
            setBody(validPayment)
        }
        assertEquals(HttpStatusCode.OK, replay.status)
        assertEquals(first.bodyAsText(), replay.bodyAsText())
    }

    // ---- Fraud rules (server is authoritative) ----

    @Test
    fun `oversized payment is blocked by fraud rules`() = testApplication {
        val response = client.post("/api/v1/payments") {
            header("Idempotency-Key", "key-fraud")
            contentType(ContentType.Application.Json)
            setBody(
                """{"fromAccountId":"acc-1","payeeName":"Jane Doe","payeeIban":"UA213223130000026007233566001","amountMinor":500000,"currency":"EUR"}"""
            )
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `velocity plus new payee escalates to a fraud block`() = testApplication {
        // Three rapid transfers to the same payee stay MEDIUM/LOW (velocity alone is 0.35)…
        repeat(3) { i ->
            assertEquals(HttpStatusCode.Created, client.postPayment("key-vel-$i"))
        }
        // …the fourth, to a brand-new payee, adds 0.20 (new payee) → 0.55 = HIGH → 403.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.postPayment("key-vel-3", payeeName = "Someone Else"),
        )
    }

    @Test
    fun `insufficient funds is rejected with 409`() = testApplication {
        // Drain acc-1 below the fraud single-transfer limit using known-payee transfers
        // (repeats to the same payee never exceed MEDIUM risk: velocity 0.35 < 0.55).
        repeat(6) { i ->
            assertEquals(HttpStatusCode.Created, client.postPayment("key-drain-$i", amountMinor = 190_000))
        }
        // Balance is now 1_245_075 - 1_140_000 = 105_075. 150_000 > balance and < fraud limit.
        assertEquals(
            HttpStatusCode.Conflict,
            client.postPayment("key-overdraft", amountMinor = 150_000),
        )
    }
}
