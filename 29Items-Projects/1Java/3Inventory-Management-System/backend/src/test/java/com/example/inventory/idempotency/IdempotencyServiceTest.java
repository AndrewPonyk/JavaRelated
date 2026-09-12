package com.example.inventory.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.inventory.common.error.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {
    @Mock private IdempotencyRecordRepository repository;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, new ObjectMapper(), Duration.ofHours(1));
    }

    @Test
    void storesAndReplaysCanonicalResponse() {
        UUID resourceId = UUID.randomUUID();
        service.store("valid-key", "create", new Request("sku"), resourceId, new Response("ok"), 201);
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        IdempotencyRecord stored = captor.getValue();
        assertThat(stored.getHttpStatus()).isEqualTo(201);
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());

        when(repository.findById("valid-key")).thenReturn(Optional.of(stored));
        assertThat(service.replay("valid-key", "create", new Request("sku"), Response.class))
                .contains(new Response("ok"));
    }

    @Test
    void rejectsKeyReuseAndUnsafeKeys() {
        IdempotencyRecord record = new IdempotencyRecord("valid-key", "other", "hash", null,
                "{}", 200, Instant.now().plusSeconds(1));
        when(repository.findById("valid-key")).thenReturn(Optional.of(record));
        assertThatThrownBy(() -> service.replay("valid-key", "create", new Request("sku"), Response.class))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.replay("short", "create", new Request("sku"), Response.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.store("unsafe key", "create", new Request("sku"), null,
                new Response("ok"), 200)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiresOldResponsesAndCleansUpTheTable() {
        IdempotencyRecord expired = new IdempotencyRecord("expired-key", "create", "hash", null,
                "{}", 200, Instant.now().minusSeconds(1));
        when(repository.findById("expired-key")).thenReturn(Optional.of(expired));
        when(repository.deleteExpired(any(Instant.class))).thenReturn(3);

        assertThat(service.replay("expired-key", "create", new Request("sku"), Response.class)).isEmpty();
        verify(repository).delete(expired);
        assertThat(service.cleanupExpiredRecords()).isEqualTo(3);
    }

    private record Request(String sku) { }
    private record Response(String result) { }
}
