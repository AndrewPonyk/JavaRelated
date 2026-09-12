package com.example.orderservice.api;

import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.OrderSummaryResponse;
import com.example.orderservice.api.dto.PageResponse;
import com.example.orderservice.api.dto.UpdateOrderStatusRequest;
import com.example.orderservice.service.CreationResult;
import com.example.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * REST edge of the Order Service. Thin by design: validation annotations,
 * status codes and URIs live here — everything else is delegated.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "E-commerce order lifecycle")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place a new order",
            description = "Creates an order in status NEW and computes the total server-side. "
                    + "Send an Idempotency-Key header to make retries safe: a replay returns the original order with 200.")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(description = "Client-generated dedupe token (e.g. a UUID); retries with the same key are replays")
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Size(max = 64, message = "Idempotency-Key must not exceed 64 characters") String idempotencyKey,
            UriComponentsBuilder uriBuilder) {
        CreationResult result = orderService.create(request, idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok(result.order());
        }
        URI location = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(result.order().id()).toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order with its items")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List orders (summaries)",
            description = "Paged; optionally filtered by customerId. Items are excluded — fetch the detail resource for lines.")
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) UUID customerId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.list(customerId, pageable);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Advance the order lifecycle",
            description = "Allowed: NEW→CONFIRMED→PAID→SHIPPED→DELIVERED; illegal moves yield 409.")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order",
            description = "Allowed from NEW or CONFIRMED; the order is kept as an audit record in status CANCELLED.")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        orderService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
