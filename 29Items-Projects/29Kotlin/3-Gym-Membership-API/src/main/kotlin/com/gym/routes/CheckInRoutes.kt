package com.gym.routes

import com.gym.domain.CheckInRequest
import com.gym.domain.OccupancyResponse
import com.gym.domain.ValidationException
import com.gym.service.CapacityService
import com.gym.service.CheckInService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.checkInRoutes() {
    val checkInService by lazy { GlobalContext.get().get<CheckInService>() }
    val capacityService by lazy { GlobalContext.get().get<CapacityService>() }

    route("/api/v1") {
        // Real-time Turnstile Badge Scan
        post("/check-in") {
            val request = call.receive<CheckInRequest>()
            val response = checkInService.processCheckIn(request)
            call.respond(HttpStatusCode.OK, response)
        }

        // Live Facility Capacity Stats
        get("/occupancy") {
            call.respond(
                HttpStatusCode.OK,
                OccupancyResponse(
                    currentOccupancy = capacityService.currentOccupancy,
                    maxCapacity = capacityService.maxCapacity,
                    isAtCapacity = capacityService.isAtCapacity()
                )
            )
        }

        // Member Check-In History
        get("/check-ins/member/{memberId}") {
            val memberId = call.parameters["memberId"]?.takeIf { it.isNotBlank() }
                ?: throw ValidationException("Missing member ID")
            val pageParam = call.request.queryParameters["page"]?.toIntOrNull()
            val pageSizeParam = call.request.queryParameters["pageSize"]?.toIntOrNull()

            if (pageParam != null || pageSizeParam != null) {
                val paginated = checkInService.getRecentCheckInsPaginated(
                    memberId = memberId,
                    page = pageParam ?: 1,
                    pageSize = pageSizeParam ?: 10
                )
                call.respond(HttpStatusCode.OK, paginated)
            } else {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val history = checkInService.getRecentCheckIns(memberId).take(limit)
                call.respond(HttpStatusCode.OK, history)
            }
        }
    }
}

