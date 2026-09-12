package com.example.inventory.audit;

import com.example.inventory.common.web.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEntryRepository repository;
    private final RequestContext requestContext;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEntryRepository repository, RequestContext requestContext,
                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.requestContext = requestContext;
        this.objectMapper = objectMapper;
    }

    public void record(String entityType, UUID entityId, String action, String reason,
                       Object before, Object after) {
        repository.save(new AuditEntry(entityType, entityId, action, requestContext.actor(), reason,
                requestContext.correlationId(), json(before), json(after)));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize audit state", exception);
        }
    }
}

