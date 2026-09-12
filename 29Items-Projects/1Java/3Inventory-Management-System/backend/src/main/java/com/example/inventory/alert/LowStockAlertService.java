package com.example.inventory.alert;

import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.common.web.RequestContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LowStockAlertService {

    private final LowStockAlertRepository repository;
    private final RequestContext requestContext;

    public LowStockAlertService(LowStockAlertRepository repository, RequestContext requestContext) {
        this.repository = repository;
        this.requestContext = requestContext;
    }

    @Transactional(readOnly = true)
    public Page<LowStockAlertResponse> list(AlertStatus status, Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDesc(status, pageable).map(this::response);
    }

    @Transactional
    public LowStockAlertResponse acknowledge(UUID id) {
        LowStockAlert alert = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Low-stock alert was not found."));
        alert.acknowledge(requestContext.actor());
        return response(repository.saveAndFlush(alert));
    }

    private LowStockAlertResponse response(LowStockAlert alert) {
        return new LowStockAlertResponse(alert.getId(), alert.getEventId(), alert.getItem().getId(),
                alert.getItem().getSku(), alert.getItem().getName(), alert.getItem().getWarehouse().getCode(),
                alert.getQuantity(), alert.getReorderPoint(), alert.getStatus(), alert.getCreatedAt(),
                alert.getAcknowledgedAt(), alert.getAcknowledgedBy());
    }
}

