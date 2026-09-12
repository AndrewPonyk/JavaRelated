package com.example.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

    private final Jwt token = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
            Map.of("alg", "none"), Map.of("sub", "operator", "aud", List.of("inventory-api")));

    @Test
    void acceptsOnlyTheConfiguredAudience() {
        assertThat(new AudienceValidator("inventory-api").validate(token).hasErrors()).isFalse();
        assertThat(new AudienceValidator("another-api").validate(token).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingConfiguration() {
        assertThatThrownBy(() -> new AudienceValidator(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
