package com.example.inventory.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reconciliation")
public class ReconciliationController {

    private final InventoryReconciliationService service;

    public ReconciliationController(InventoryReconciliationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:admin')")
    public InventoryReconciliationService.ReconciliationReport reconcile(
            @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int maxResults) {
        return service.reconcile(maxResults);
    }
}
