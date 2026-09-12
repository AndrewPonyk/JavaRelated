package com.gym.routes

import com.gym.domain.CreateSubscriptionRequest
import com.gym.domain.FreezeSubscriptionRequest
import com.gym.domain.GuestPassResponse
import com.gym.domain.ValidationException
import com.gym.service.SubscriptionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.subscriptionRoutes() {
    val subscriptionService by lazy { GlobalContext.get().get<SubscriptionService>() }

    route("/api/v1") {
        // Public plans catalog
        get("/plans") {
            val plans = subscriptionService.getAllPlans()
            call.respond(HttpStatusCode.OK, plans)
        }

        get("/plans/{id}") {
            val id = call.parameters["id"]?.takeIf { it.isNotBlank() }
                ?: throw ValidationException("Missing plan ID")
            val plan = subscriptionService.getPlanById(id)
            call.respond(HttpStatusCode.OK, plan)
        }

        authenticate("auth-jwt") {
            route("/subscriptions") {
                post {
                    val request = call.receive<CreateSubscriptionRequest>()
                    val sub = subscriptionService.createSubscription(request)
                    call.respond(HttpStatusCode.Created, sub)
                }

                get("/member/{memberId}") {
                    val memberId = call.parameters["memberId"]?.takeIf { it.isNotBlank() }
                        ?: throw ValidationException("Missing member ID")
                    val sub = subscriptionService.getActiveSubscription(memberId)
                    call.respond(HttpStatusCode.OK, sub)
                }

                post("/member/{memberId}/freeze") {
                    val memberId = call.parameters["memberId"]?.takeIf { it.isNotBlank() }
                        ?: throw ValidationException("Missing member ID")
                    val request = call.receive<FreezeSubscriptionRequest>()
                    val freezeRecord = subscriptionService.freezeMembership(memberId, request)
                    call.respond(HttpStatusCode.OK, freezeRecord)
                }

                post("/member/{memberId}/unfreeze") {
                    val memberId = call.parameters["memberId"]?.takeIf { it.isNotBlank() }
                        ?: throw ValidationException("Missing member ID")
                    val sub = subscriptionService.unfreezeMembership(memberId)
                    call.respond(HttpStatusCode.OK, sub)
                }

                post("/member/{memberId}/guest-pass") {
                    val memberId = call.parameters["memberId"]?.takeIf { it.isNotBlank() }
                        ?: throw ValidationException("Missing member ID")
                    val remaining = subscriptionService.useGuestPass(memberId)
                    call.respond(
                        HttpStatusCode.OK,
                        GuestPassResponse(
                            message = "Guest pass redeemed successfully",
                            remainingGuestPasses = remaining
                        )
                    )
                }
            }
        }
    }
}

