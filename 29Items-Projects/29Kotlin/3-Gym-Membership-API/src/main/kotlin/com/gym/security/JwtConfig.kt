package com.gym.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.gym.domain.Member
import com.gym.domain.Role
import java.util.*

class JwtConfig(
    private val secret: String = "gym-api-super-secret-key-32-chars-minimum",
    private val issuer: String = "https://api.gymmembership.com/",
    private val audience: String = "gym-api-consumers",
    private val validityMs: Long = 24 * 60 * 60 * 1000L // 24 hours
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    fun generateToken(member: Member): String {
        val now = Date()
        val expiresAt = Date(now.time + validityMs)
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("memberId", member.id)
            .withClaim("email", member.email)
            .withClaim("role", member.role.name)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }
}
