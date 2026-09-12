package com.example.bank.backend.api

import com.example.bank.backend.model.ErrorEnvelope
import com.example.bank.backend.model.PaymentResult
import com.example.bank.backend.model.PaymentRequest
import com.example.bank.backend.service.BankService
import com.example.bank.backend.model.validate
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.accounts(service: BankService) {
    get("/api/v1/accounts") {
        call.respond(service.listAccounts())
    }

    // Documented contract (ARCHITECTURE.md 2.2) — the statements screen's data source.
    get("/api/v1/accounts/{id}/transactions") {
        val accountId = call.parameters["id"]
        if (accountId.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorEnvelope("Account id is required"))
            return@get
        }
        val rawLimit = call.request.queryParameters["limit"]
        val limit = if (rawLimit == null) {
            DEFAULT_TRANSACTION_LIMIT
        } else {
            rawLimit.toIntOrNull()?.takeIf { it in 1..MAX_TRANSACTION_LIMIT }
                ?: run {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorEnvelope("limit must be an integer between 1 and $MAX_TRANSACTION_LIMIT"),
                    )
                    return@get
                }
        }
        val transactions = service.listTransactions(accountId, limit)
            ?: run {
                call.respond(HttpStatusCode.NotFound, ErrorEnvelope("Unknown account $accountId"))
                return@get
            }
        call.respond(transactions)
    }
}

private const val DEFAULT_TRANSACTION_LIMIT = 50
private const val MAX_TRANSACTION_LIMIT = 500

fun Route.payments(service: BankService) {
    route("/api/v1/payments") {

        get {
            val accountId = call.request.queryParameters["accountId"]
            if (accountId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorEnvelope("accountId query parameter is required"))
                return@get
            }
            call.respond(service.listPayments(accountId))
        }

        post {
            val idempotencyKey = call.request.header("Idempotency-Key")?.takeIf { it.isNotBlank() }
            if (idempotencyKey == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorEnvelope("Idempotency-Key header is required"))
                return@post
            }

            val request = call.receive<PaymentRequest>()
            val issues = request.validate()
            if (issues.isNotEmpty()) {
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorEnvelope(issues.joinToString("; ")))
                return@post
            }

            when (val result = service.submitPayment(idempotencyKey, request)) {
                is PaymentResult.Created -> call.respond(HttpStatusCode.Created, result.payment)
                is PaymentResult.Duplicate -> call.respond(HttpStatusCode.OK, result.payment)
                is PaymentResult.Rejected.InsufficientFunds ->
                    call.respond(HttpStatusCode.Conflict, ErrorEnvelope("Insufficient funds"))
                is PaymentResult.Rejected.FraudBlocked ->
                    call.respond(HttpStatusCode.Forbidden, ErrorEnvelope(result.reason))
                is PaymentResult.Rejected.UnknownAccount ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ErrorEnvelope("Unknown account ${result.accountId}"))
            }
        }
    }
}
