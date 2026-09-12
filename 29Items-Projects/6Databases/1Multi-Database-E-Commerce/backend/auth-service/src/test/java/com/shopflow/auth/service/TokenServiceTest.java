package com.shopflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shopflow.common.error.ApiException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private TokenService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        service = new TokenService(
                redis,
                "test-secret-that-is-long-enough-for-hs256-signing-32b",
                "https://auth.shopflow.local",
                900, 1209600);
    }

    @Test
    void accessToken_roundTrips_subjectAndRoles() {
        String token = service.issueAccessToken("alice", "ROLE_CUSTOMER");

        Claims claims = service.validateAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("roles", String.class)).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    void validateAccessToken_tampered_throws401() {
        String token = service.issueAccessToken("alice", "ROLE_CUSTOMER");
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertThatThrownBy(() -> service.validateAccessToken(tampered))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void rotateRefreshToken_valid_returnsUserId_andDeletesOld() {
        when(ops.get("refresh:tok-1")).thenReturn("alice");

        String userId = service.rotateRefreshToken("tok-1");

        assertThat(userId).isEqualTo("alice");
        org.mockito.Mockito.verify(redis).delete("refresh:tok-1");
    }

    @Test
    void rotateRefreshToken_unknown_throws401() {
        when(ops.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.rotateRefreshToken("nope"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid or expired");
    }

    @Test
    void issueRefreshToken_storesWithTtl() {
        String token = service.issueRefreshToken("bob");

        assertThat(token).isNotBlank();
        org.mockito.Mockito.verify(ops)
                .set(eq("refresh:" + token), eq("bob"),
                        org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    @Test
    void revokeRefreshToken_deletesStoredKey() {
        service.revokeRefreshToken("tok-x");

        org.mockito.Mockito.verify(redis).delete("refresh:tok-x");
    }
}
