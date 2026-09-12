package com.example.bank.backend.plugins

import com.example.bank.backend.api.accounts
import com.example.bank.backend.api.payments
import com.example.bank.backend.data.InMemoryPaymentRepository
import com.example.bank.backend.data.PaymentRepository
import com.example.bank.backend.service.BankService
import com.example.bank.backend.service.FraudDetectionService
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.Clock

fun Application.configureRouting(
    clock: Clock = Clock.systemDefaultZone(),
    repository: PaymentRepository = InMemoryPaymentRepository(clock),
) {
    val bankService = BankService(
        repository = repository,
        fraudService = FraudDetectionService(clock),
    )

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        accounts(bankService)
        payments(bankService)
    }
}
