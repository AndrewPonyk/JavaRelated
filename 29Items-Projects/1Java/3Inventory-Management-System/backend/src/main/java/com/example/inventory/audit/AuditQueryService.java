package com.example.inventory.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditEntryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditQueryService(AuditEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> find(String entityType, UUID entityId, Pageable pageable) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId, pageable)
                .map(this::response);
    }

    private AuditResponse response(AuditEntry entry) {
        return new AuditResponse(entry.getId(), entry.getEntityType(), entry.getEntityId(), entry.getAction(),
                entry.getActor(), entry.getReason(), entry.getCorrelationId(), json(entry.getBeforeState()),
                json(entry.getAfterState()), entry.getOccurredAt());
    }

    private JsonNode json(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored audit JSON is invalid", exception);
        }
    }
}

