package com.example.orderservice.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderItemRequest;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.api.dto.UpdateOrderStatusRequest;
import com.example.orderservice.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack integration tests: real HTTP → real service → real PostgreSQL 16
 * (Testcontainers) with Flyway migrations applied and Hibernate schema validation on.
 * PATCH is exercised through Apache HttpClient 5 (the JDK client cannot send PATCH).
 *
 * <p>Runs under the failsafe profile (needs a Docker daemon):
 * {@code mvn verify -Pintegration-tests} — also enabled in ci.yml.</p>
 *
 * <p>No JWT issuer is configured here, so the open security fallback chain is active —
 * matching local/compose. JWT enforcement is covered by {@code SecurityFilterChainTest}.</p>
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")   // random management port, injected below
class OrderApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @LocalManagementPort
    private int managementPort;

    private CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(UUID.randomUUID(), "USD", List.of(
                new OrderItemRequest("SKU-1001", "Mechanical Keyboard", 1, new BigDecimal("89.90")),
                new OrderItemRequest("SKU-2002", "USB-C Cable", 2, new BigDecimal("9.99"))));
    }

    @Test
    void readinessIsUpOnTheManagementPortOnceMigrationsRan() {
        String url = "http://localhost:" + managementPort + "/actuator/health/readiness";
        ResponseEntity<String> health = rest.getForEntity(url, String.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }

    @Test
    void actuatorIsNotServedOnTheApiPort() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createReadCancelRoundTripAgainstRealDatabase() {
        // create
        ResponseEntity<OrderResponse> created = rest.postForEntity("/api/v1/orders", sampleRequest(), OrderResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        OrderResponse order = created.getBody();
        assertThat(order).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.totalAmount()).isEqualByComparingTo("109.88");

        // read back (exercises @EntityGraph fetch + JSON round-trip)
        OrderResponse fetched = rest.getForObject("/api/v1/orders/{id}", OrderResponse.class, order.id());
        assertThat(fetched.items()).hasSize(2);

        // cancel and verify the terminal state was persisted
        rest.delete("/api/v1/orders/{id}", order.id());
        OrderResponse cancelled = rest.getForObject("/api/v1/orders/{id}", OrderResponse.class, order.id());
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void statusPatchRoundTripEnforcesTheStateMachine() {
        UUID id = rest.postForEntity("/api/v1/orders", sampleRequest(), OrderResponse.class).getBody().id();

        // legal transition NEW → CONFIRMED
        OrderResponse confirmed = rest.patchForObject("/api/v1/orders/{id}/status",
                new UpdateOrderStatusRequest(OrderStatus.CONFIRMED), OrderResponse.class, id);
        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);

        // illegal jump CONFIRMED → DELIVERED is rejected with an RFC-7807 conflict
        ResponseEntity<String> conflict = rest.exchange("/api/v1/orders/{id}/status", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateOrderStatusRequest(OrderStatus.DELIVERED)), String.class, id);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("Invalid status transition");
    }

    @Test
    void idempotencyKeyMakesCreateReplaySafe() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        CreateOrderRequest request = sampleRequest();

        ResponseEntity<OrderResponse> first = rest.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, headers), OrderResponse.class);
        ResponseEntity<OrderResponse> replay = rest.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, headers), OrderResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);   // replay, not a duplicate
        assertThat(replay.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void unknownOrderYields404Problem() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/orders/{id}", String.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Order not found");
    }
}
