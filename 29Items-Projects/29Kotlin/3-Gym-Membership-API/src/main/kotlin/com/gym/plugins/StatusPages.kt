package com.gym.plugins

import com.gym.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("StatusPages")

    install(StatusPages) {
        exception<MemberNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(HttpStatusCode.NotFound.value, "NOT_FOUND", cause.message ?: "Member not found")
            )
        }
        exception<MemberAlreadyExistsException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(HttpStatusCode.Conflict.value, "MEMBER_ALREADY_EXISTS", cause.message ?: "Conflict")
            )
        }
        exception<InvalidCredentialsException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(HttpStatusCode.Unauthorized.value, "UNAUTHORIZED", cause.message ?: "Invalid credentials")
            )
        }
        exception<NoActiveSubscriptionException> { call, cause ->
            call.respond(
                HttpStatusCode.PaymentRequired,
                ErrorResponse(HttpStatusCode.PaymentRequired.value, "NO_ACTIVE_SUBSCRIPTION", cause.message ?: "No active subscription")
            )
        }
        exception<MembershipFrozenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(HttpStatusCode.Forbidden.value, "MEMBERSHIP_FROZEN", cause.message ?: "Membership is frozen")
            )
        }
        exception<FreezeLimitExceededException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(HttpStatusCode.BadRequest.value, "FREEZE_LIMIT_EXCEEDED", cause.message ?: "Freeze limit exceeded")
            )
        }
        exception<FeatureGatedException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(HttpStatusCode.Forbidden.value, "FEATURE_GATED", cause.message ?: "Feature not included in tier")
            )
        }
        exception<CapacityExceededException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(HttpStatusCode.Conflict.value, "CAPACITY_EXCEEDED", cause.message ?: "Gym is at max capacity")
            )
        }
        exception<BadgeSharingDetectedException> { call, cause ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse(HttpStatusCode.TooManyRequests.value, "ANTI_PASSBACK_VIOLATION", cause.message ?: "Badge sharing detected")
            )
        }
        exception<InsufficientGuestPassesException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(HttpStatusCode.BadRequest.value, "INSUFFICIENT_GUEST_PASSES", cause.message ?: "No guest passes remaining")
            )
        }
        exception<PlanNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(HttpStatusCode.NotFound.value, "PLAN_NOT_FOUND", cause.message ?: "Plan not found")
            )
        }
        exception<SubscriptionNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(HttpStatusCode.NotFound.value, "SUBSCRIPTION_NOT_FOUND", cause.message ?: "Subscription not found")
            )
        }
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(HttpStatusCode.BadRequest.value, "VALIDATION_ERROR", cause.message ?: "Validation failed")
            )
        }
        exception<DuplicateSubscriptionException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(HttpStatusCode.Conflict.value, "DUPLICATE_SUBSCRIPTION", cause.message ?: "Duplicate subscription")
            )
        }
        exception<UnauthorizedAccessException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(HttpStatusCode.Forbidden.value, "FORBIDDEN", cause.message ?: "Forbidden")
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(HttpStatusCode.BadRequest.value, "BAD_REQUEST", cause.message ?: "Invalid request")
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(HttpStatusCode.BadRequest.value, "BAD_REQUEST", cause.message ?: "Invalid state")
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception on URI ${call.request.uri}: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(HttpStatusCode.InternalServerError.value, "INTERNAL_SERVER_ERROR", cause.message ?: "An unexpected error occurred")
            )
        }
    }
}

