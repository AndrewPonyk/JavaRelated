package com.example.inventory.stock.web;

import com.example.inventory.common.web.PageResponse;
import com.example.inventory.inventory.dto.InventoryResponse;
import com.example.inventory.stock.dto.AdjustmentRequest;
import com.example.inventory.stock.dto.BulkAdjustmentRequest;
import com.example.inventory.stock.dto.BulkAdjustmentResponse;
import com.example.inventory.stock.dto.QuantityCommandRequest;
import com.example.inventory.stock.dto.ReservationCreateRequest;
import com.example.inventory.stock.dto.ReservationResponse;
import com.example.inventory.stock.dto.StockMovementResponse;
import com.example.inventory.stock.dto.TransferRequest;
import com.example.inventory.stock.dto.TransferResponse;
import com.example.inventory.stock.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/inventory/{itemId}/adjustments")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public InventoryResponse adjust(@RequestHeader("Idempotency-Key") String key,
                                    @PathVariable UUID itemId,
                                    @Valid @RequestBody AdjustmentRequest request) {
        return stockService.adjust(key, itemId, request);
    }

    @PostMapping("/inventory/{itemId}/receipts")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public InventoryResponse receive(@RequestHeader("Idempotency-Key") String key,
                                     @PathVariable UUID itemId,
                                     @Valid @RequestBody QuantityCommandRequest request) {
        return stockService.receive(key, itemId, request);
    }

    @PostMapping("/inventory/{itemId}/shipments")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public InventoryResponse ship(@RequestHeader("Idempotency-Key") String key,
                                  @PathVariable UUID itemId,
                                  @Valid @RequestBody QuantityCommandRequest request) {
        return stockService.ship(key, itemId, request);
    }

    @PostMapping("/inventory/bulk-adjustments")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public BulkAdjustmentResponse bulkAdjust(@RequestHeader("Idempotency-Key") String key,
                                             @Valid @RequestBody BulkAdjustmentRequest request) {
        return stockService.bulkAdjust(key, request);
    }

    @PostMapping("/inventory/{itemId}/reservations")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public ResponseEntity<ReservationResponse> reserve(
            @RequestHeader("Idempotency-Key") String key,
            @PathVariable UUID itemId,
            @Valid @RequestBody ReservationCreateRequest request) {
        ReservationResponse response = stockService.reserve(key, itemId, request);
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + response.id())).body(response);
    }

    @GetMapping("/inventory/{itemId}/reservations")
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public PageResponse<ReservationResponse> reservations(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(stockService.reservations(itemId, PageRequest.of(page, size)));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public ReservationResponse release(@RequestHeader("Idempotency-Key") String key,
                                       @PathVariable UUID reservationId) {
        return stockService.release(key, reservationId);
    }

    @PostMapping("/reservations/{reservationId}/fulfill")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public ReservationResponse fulfill(@RequestHeader("Idempotency-Key") String key,
                                       @PathVariable UUID reservationId) {
        return stockService.fulfill(key, reservationId);
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('SCOPE_inventory:write')")
    public ResponseEntity<TransferResponse> transfer(@RequestHeader("Idempotency-Key") String key,
                                                     @Valid @RequestBody TransferRequest request) {
        TransferResponse response = stockService.transfer(key, request);
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + response.transferId())).body(response);
    }

    @GetMapping("/inventory/{itemId}/movements")
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public PageResponse<StockMovementResponse> movements(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.from(stockService.movements(itemId, PageRequest.of(page, size)));
    }
}
