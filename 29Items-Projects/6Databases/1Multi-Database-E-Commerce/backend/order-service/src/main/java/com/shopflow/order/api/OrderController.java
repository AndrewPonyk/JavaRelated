package com.shopflow.order.api;

import com.shopflow.common.dto.ApiResponse;
import com.shopflow.order.api.dto.CreateOrderRequest;
import com.shopflow.order.api.dto.OrderResponse;
import com.shopflow.order.domain.Order;
import com.shopflow.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for orders. Thin controller: validates input ({@code @Valid}),
 * delegates to {@link OrderService}, and maps domain objects to
 * {@link OrderResponse} wrapped in the shared {@link ApiResponse} envelope.
 * Errors are handled centrally by the shared {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create an order. Returns 201 with a Location header and the created resource.
     * An optional {@code Idempotency-Key} header makes retries safe (no double order).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Order order = orderService.createOrder(request, idempotencyKey);
        OrderResponse body = OrderResponse.from(order);
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + order.getId()))
                .body(ApiResponse.ok(body));
    }

    /** Fetch a single order by id. 404 (via ApiException) if absent. */
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable String id) {
        return ApiResponse.ok(OrderResponse.from(orderService.getOrder(id)));
    }

    /** List a customer's orders, newest first, paginated. */
    @GetMapping
    public ApiResponse<Page<OrderResponse>> listForCustomer(
            @RequestParam String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<OrderResponse> result = orderService
                .listForCustomer(customerId, PageRequest.of(page, Math.min(size, 100)))
                .map(OrderResponse::from);
        return ApiResponse.ok(result);
    }

    /** Mark an order as paid (PENDING → PAID). */
    @PostMapping("/{id}/payment")
    public ApiResponse<OrderResponse> markPaid(@PathVariable String id) {
        return ApiResponse.ok(OrderResponse.from(orderService.markPaid(id)));
    }

    /** Mark an order as fulfilled/shipped (PAID → FULFILLED). */
    @PostMapping("/{id}/fulfillment")
    public ApiResponse<OrderResponse> fulfill(@PathVariable String id) {
        return ApiResponse.ok(OrderResponse.from(orderService.fulfill(id)));
    }

    /** Cancel an order (PENDING/PAID → CANCELLED). */
    @PostMapping("/{id}/cancellation")
    public ApiResponse<OrderResponse> cancel(@PathVariable String id) {
        return ApiResponse.ok(OrderResponse.from(orderService.cancel(id)));
    }
}
