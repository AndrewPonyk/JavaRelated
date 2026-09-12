package com.shopflow.auth.service;

import com.shopflow.common.error.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Issues signed JWT access tokens and manages opaque refresh tokens in Redis.
 *
 * <p>Refresh tokens are stored with a TTL (auto-expiry) and rotated on use to
 * limit replay. Access tokens are stateless HMAC-signed JWTs, validated here and
 * at the gateway using the same shared secret.
 */
@Service
public class TokenService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public TokenService(
            StringRedisTemplate redis,
            @Value("${JWT_SECRET:change-me-with-a-256-bit-secret-value-please}") String secret,
            @Value("${JWT_ISSUER:https://auth.shopflow.local}") String issuer,
            @Value("${JWT_ACCESS_TTL_SECONDS:900}") long accessTtlSeconds,
            @Value("${JWT_REFRESH_TTL_SECONDS:1209600}") long refreshTtlSeconds) {
        this.redis = redis;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
        this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
    }

    /** Mint a signed access token carrying the user's id and roles. */
    public String issueAccessToken(String userId, String roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(signingKey)
                .compact();
    }

    /** Create + store a refresh token in Redis with TTL; returns the opaque token. */
    public String issueRefreshToken(String userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(REFRESH_KEY_PREFIX + token, userId, refreshTtl);
        return token;
    }

    /**
     * Validate a refresh token, rotate it (single-use), and return the user id.
     * Throws 401 if the token is unknown/expired.
     */
    public String rotateRefreshToken(String oldToken) {
        String key = REFRESH_KEY_PREFIX + oldToken;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH",
                    "Refresh token is invalid or expired");
        }
        redis.delete(key);
        return userId;
    }

    /** Invalidate a refresh token (logout). No-op if already gone. */
    public void revokeRefreshToken(String token) {
        redis.delete(REFRESH_KEY_PREFIX + token);
    }

    /** Parse + verify an access token, returning its claims. Throws 401 if invalid. */
    public Claims validateAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Access token is invalid or expired");
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
