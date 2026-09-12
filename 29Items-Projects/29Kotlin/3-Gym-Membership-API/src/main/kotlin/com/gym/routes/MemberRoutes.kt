package com.gym.routes

import com.gym.domain.ValidationException
import com.gym.service.MemberService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.memberRoutes() {
    val memberService by lazy { GlobalContext.get().get<MemberService>() }

    route("/api/v1/members") {
        authenticate("auth-jwt") {
            get {
                val pageParam = call.request.queryParameters["page"]?.toIntOrNull()
                val pageSizeParam = call.request.queryParameters["pageSize"]?.toIntOrNull()

                if (pageParam != null || pageSizeParam != null) {
                    val paginated = memberService.getMembersPaginated(
                        page = pageParam ?: 1,
                        pageSize = pageSizeParam ?: 20
                    )
                    call.respond(HttpStatusCode.OK, paginated)
                } else {
                    val members = memberService.getAllMembers()
                    call.respond(HttpStatusCode.OK, members)
                }
            }

            get("/{id}") {
                val id = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: throw ValidationException("Missing member ID")
                val member = memberService.getMemberById(id)
                call.respond(HttpStatusCode.OK, member)
            }

            get("/badge/{badgeCode}") {
                val badgeCode = call.parameters["badgeCode"]?.takeIf { it.isNotBlank() }
                    ?: throw ValidationException("Missing badge code")
                val member = memberService.getMemberByBadge(badgeCode)
                call.respond(HttpStatusCode.OK, member)
            }
        }
    }
}

