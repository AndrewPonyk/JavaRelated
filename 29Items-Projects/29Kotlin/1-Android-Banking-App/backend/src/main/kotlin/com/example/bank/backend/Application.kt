package com.example.bank.backend

import com.example.bank.backend.data.DatabaseConfig
import com.example.bank.backend.data.DatabaseFactory
import com.example.bank.backend.data.InMemoryPaymentRepository
import com.example.bank.backend.data.PaymentRepository
import com.example.bank.backend.plugins.configureMonitoring
import com.example.bank.backend.plugins.configureRouting
import com.example.bank.backend.plugins.configureSerialization
import com.example.bank.backend.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import java.time.Clock

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureStatusPages()

    val clock = Clock.systemDefaultZone()
    val repository: PaymentRepository = runCatching {
        val db = environment.config.config("bank.database")
        DatabaseConfig(
            enabled = db.property("enabled").getString().toBoolean(),
            url = db.property("url").getString(),
            user = db.property("user").getString(),
            password = db.property("password").getString(),
        )
    }.getOrNull()?.takeIf { it.enabled }?.let { DatabaseFactory.connect(it, clock) }
        ?: InMemoryPaymentRepository(clock) // default: zero-dependency demo mode (tests, :backend:run)

    configureRouting(clock = clock, repository = repository)
}
