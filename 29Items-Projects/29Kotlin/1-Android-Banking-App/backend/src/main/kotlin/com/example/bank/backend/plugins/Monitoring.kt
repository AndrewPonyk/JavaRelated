package com.example.bank.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallId) {
        // One correlation id per request — threaded through logs and echoed to the client.
        retrieveFromHeader("X-Call-Id")
        replyToHeader("X-Call-Id")
        generate(10)
    }
    // NOTE: the CallLogging package is genuinely misspelled "callloging" in Ktor 2.x;
    // callIdMdc (which bridges CallId into the log MDC) lives in the callid package.
    install(io.ktor.server.plugins.callloging.CallLogging) {
        callIdMdc("call-id")
        level = Level.INFO
        format { call ->
            "cid=${call.callId} ${call.request.httpMethod.value} ${call.request.path()} status=${call.response.status()?.value}"
        }
        logger = LoggerFactory.getLogger("bank.api")
    }
}
