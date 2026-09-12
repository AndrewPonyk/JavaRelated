package com.gym.plugins

import com.gym.security.JwtConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.core.context.GlobalContext

fun Application.configureSecurity() {
    val jwtConfig by lazy { GlobalContext.get().get<JwtConfig>() }

    authentication {
        jwt("auth-jwt") {
            realm = "GymMembershipRealm"
            verifier(jwtConfig.verifier)
            validate { credential ->
                val memberId = credential.payload.getClaim("memberId").asString()
                val email = credential.payload.getClaim("email").asString()
                if (!memberId.isNullOrEmpty() && !email.isNullOrEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
