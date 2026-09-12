package com.example.orderservice.service;

import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.OrderSummaryResponse;
import com.example.orderservice.api.dto.PageResponse;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service: transaction boundaries and orchestration.
 * Business rules live in the {@link Order} aggregate; HTTP concerns in the controller.
 * {@link Clock} is injected so time-dependent behavior is exactly testable.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final Clock clock;

    public OrderService(OrderRepository repository, OrderMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Creates an order; when {@code idempotencyKey} is supplied, retries with the same
     * key return the original order instead of inserting a duplicate.
     *
     * <p>Runs OUTSIDE a surrounding transaction on purpose: recovering from the unique-index
     * race requires discarding the failed insert's persistence context, so the lookup, the
     * insert ({@code saveAndFlush}) and the race-recovery lookup each run in their own short
     * transaction. The insert itself (order + items cascade) remains a single atomic write.</p>
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CreationResult create(CreateOrderRequest request, String idempotencyKey) {
        String key = normalize(idempotencyKey);

        if (key != null) {
            Optional<Order> existing = repository.findWithItemsByCustomerIdAndIdempotencyKey(request.customerId(), key);
            if (existing.isPresent()) {
                return new CreationResult(mapper.toResponse(existing.get()), true);
            }
        }

        Order order = mapper.toEntity(request);
        order.setIdempotencyKey(key);
        order.recalculateTotal();
        Instant now = clock.instant();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        try {
            return new CreationResult(mapper.toResponse(repository.saveAndFlush(order)), false);
        } catch (DataIntegrityViolationException raceLoser) {
            // A concurrent request with the same key won the insert — serve the winner.
            return repository.findWithItemsByCustomerIdAndIdempotencyKey(request.customerId(), key)
                    .map(winner -> new CreationResult(mapper.toResponse(winner), true))
                    .orElseThrow(() -> raceLoser);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return repository.findWithItemsById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID customerId, Pageable pageable) {
        Page<Order> page = customerId == null
                ? repository.findAll(pageable)
                : repository.findByCustomerId(customerId, pageable);
        Page<OrderSummaryResponse> mapped = page.map(mapper::toSummary);
        return new PageResponse<>(
                mapped.getContent(),
                mapped.getNumber(),
                mapped.getSize(),
                mapped.getTotalElements(),
                mapped.getTotalPages());
    }

    /** Applies a state-machine transition; the aggregate rejects illegal moves with a 409-mapped exception. */
    public OrderResponse updateStatus(UUID id, OrderStatus target) {
        Order order = repository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.transitionTo(target);
        order.setUpdatedAt(clock.instant());
        return mapper.toResponse(order);
    }

    /** Cancellation is just a transition — allowed from NEW/CONFIRMED only. */
    public OrderResponse cancel(UUID id) {
        return updateStatus(id, OrderStatus.CANCELLED);
    }

    private static String normalize(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }
}
