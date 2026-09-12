package com.gym.routes

import com.gym.domain.*
import com.gym.security.JwtConfig
import com.gym.service.MemberService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext

fun Route.authRoutes() {
    val memberService by lazy { GlobalContext.get().get<MemberService>() }
    val jwtConfig by lazy { GlobalContext.get().get<JwtConfig>() }

    route("/api/v1/auth") {
        post("/register") {
            val request = call.receive<RegisterMemberRequest>()
            val member = memberService.register(request)
            val token = jwtConfig.generateToken(member)
            call.respond(HttpStatusCode.Created, AuthResponse(token, member))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val member = memberService.authenticate(request)
            val token = jwtConfig.generateToken(member)
            call.respond(HttpStatusCode.OK, AuthResponse(token, member))
        }
    }
}
