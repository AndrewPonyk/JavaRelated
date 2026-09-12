package com.example.inventory.idempotency;

import com.example.inventory.common.error.ConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Duration retention;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper,
                              @Value("${app.idempotency.retention:PT24H}") Duration retention) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.retention = retention;
    }

    @Transactional
    public <T> Optional<T> replay(String key, String operation, Object request, Class<T> responseType) {
        validateKey(key);
        String requestHash = hash(request);
        Optional<IdempotencyRecord> stored = repository.findById(key);
        if (stored.isPresent() && stored.get().getExpiresAt().isBefore(Instant.now())) {
            repository.delete(stored.get());
            return Optional.empty();
        }
        return stored.map(record -> {
            if (!record.getOperation().equals(operation) || !record.getRequestHash().equals(requestHash)) {
                throw new ConflictException("Idempotency key was already used for a different request.");
            }
            try {
                return objectMapper.readValue(record.getResponseBody(), responseType);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored idempotency response is invalid", exception);
            }
        });
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 15 * * * *}")
    public int cleanupExpiredRecords() {
        return repository.deleteExpired(Instant.now());
    }

    public void store(String key, String operation, Object request, UUID resourceId,
                      Object response, int httpStatus) {
        validateKey(key);
        try {
            repository.save(new IdempotencyRecord(key, operation, hash(request), resourceId,
                    objectMapper.writeValueAsString(response), httpStatus, Instant.now().plus(retention)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize idempotency response", exception);
        }
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 8-128 safe characters.");
        }
    }

    private String hash(Object request) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash idempotent request", exception);
        }
    }
}
