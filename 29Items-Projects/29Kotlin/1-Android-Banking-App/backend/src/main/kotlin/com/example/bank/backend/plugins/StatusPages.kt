package com.example.bank.backend.plugins

import com.example.bank.backend.model.ErrorEnvelope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorEnvelope("Malformed request body"))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ErrorEnvelope("Unsupported content type"))
        }
        exception<Throwable> { call, cause ->
            // Full stack trace only server-side, keyed by the call id the client also received.
            call.application.log.error("Unhandled error [callId=${call.callId}]", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorEnvelope("Internal error"))
        }
    }
}
