package com.example.inventory.inventory.web;

import com.example.inventory.common.web.PageResponse;
import com.example.inventory.inventory.dto.InventoryCreateRequest;
import com.example.inventory.inventory.dto.InventoryResponse;
import com.example.inventory.inventory.dto.InventoryUpdateRequest;
import com.example.inventory.inventory.dto.ItemDeactivateRequest;
import com.example.inventory.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final Set<String> SORT_FIELDS = Set.of("sku", "name", "quantity", "updatedAt");
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "Search and page inventory items")
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public PageResponse<InventoryResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "sku") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        if (!SORT_FIELDS.contains(sort)) {
            throw new IllegalArgumentException("Unsupported sort field.");
        }
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageResponse.from(inventoryService.list(warehouseId, query, active,
                PageRequest.of(page, size, Sort.by(sortDirection, sort))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public ResponseEntity<InventoryResponse> get(@PathVariable UUID id) {
        InventoryResponse response = inventoryService.get(id);
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public InventoryResponse findByBarcode(@PathVariable @Size(max = 128) String barcode,
                                           @RequestParam(required = false) UUID warehouseId) {
        return inventoryService.findByBarcode(barcode, warehouseId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public ResponseEntity<InventoryResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryCreateRequest request) {
        InventoryResponse created = inventoryService.create(idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/inventory/" + created.id()))
                .eTag("\"" + created.version() + "\"").body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public ResponseEntity<InventoryResponse> update(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody InventoryUpdateRequest request) {
        InventoryResponse response = inventoryService.update(idempotencyKey, id, request);
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_inventory:admin')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id,
                                           @Valid @RequestBody ItemDeactivateRequest request) {
        inventoryService.deactivate(id, request);
        return ResponseEntity.noContent().build();
    }
}

