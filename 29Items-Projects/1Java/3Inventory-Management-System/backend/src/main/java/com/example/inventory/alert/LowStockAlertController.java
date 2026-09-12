package com.example.inventory.alert;

import com.example.inventory.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
public class LowStockAlertController {

    private final LowStockAlertService service;

    public LowStockAlertController(LowStockAlertService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public PageResponse<LowStockAlertResponse> list(
            @RequestParam(defaultValue = "OPEN") AlertStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(status, PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public LowStockAlertResponse acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id);
    }
}
