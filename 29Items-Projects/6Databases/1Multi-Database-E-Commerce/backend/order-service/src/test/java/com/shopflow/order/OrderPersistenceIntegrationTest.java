package com.shopflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflow.common.error.ApiException;
import com.shopflow.order.api.dto.CreateOrderRequest;
import com.shopflow.order.domain.Order;
import com.shopflow.order.domain.OrderStatus;
import com.shopflow.order.messaging.OrderEventPublisher;
import com.shopflow.order.repository.OrderRepository;
import com.shopflow.order.service.OrderService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full integration test against a real Oracle database (Testcontainers). Exercises
 * Flyway migrations, JPA persistence, idempotent creation, and the order lifecycle.
 * The Kafka publisher is mocked so the test is about the transactional core.
 */
@SpringBootTest
@Testcontainers
class OrderPersistenceIntegrationTest {

    @Container
    static final OracleContainer ORACLE = new OracleContainer(
            DockerImageName.parse("gvenzl/oracle-free:23-slim"))
            .withUsername("shopflow")
            .withPassword("shopflow")
            // Oracle's first-boot DB init can be slow under load; give it room.
            .withStartupTimeout(java.time.Duration.ofMinutes(5));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:59092"); // unused (publisher mocked)
    }

    @MockBean
    @SuppressWarnings("unused")
    private OrderEventPublisher orderEventPublisher;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private CreateOrderRequest request() {
        return new CreateOrderRequest("cust-int", "EUR", List.of(
                new CreateOrderRequest.Item("prod-A", 2, new BigDecimal("10.00")),
                new CreateOrderRequest.Item("prod-B", 1, new BigDecimal("5.50"))));
    }

    @Test
    void create_persistsOrderWithItemsAndTotal() {
        Order created = orderService.createOrder(request());

        Order loaded = orderRepository.findById(created.getId()).orElseThrow();
        assertThat(loaded.getTotalAmount()).isEqualByComparingTo("25.50");
        assertThat(loaded.getItems()).hasSize(2);
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void idempotencyKey_preventsDuplicateOrders() {
        String key = "idem-" + System.nanoTime();

        Order first = orderService.createOrder(request(), key);
        Order second = orderService.createOrder(request(), key);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(orderRepository.findByIdempotencyKey(key)).isPresent();
    }

    @Test
    void lifecycle_persistsTransitions() {
        Order created = orderService.createOrder(request());

        orderService.markPaid(created.getId());
        orderService.fulfill(created.getId());

        assertThat(orderRepository.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void getOrder_missing_throwsNotFound() {
        assertThatThrownBy(() -> orderService.getOrder("nonexistent"))
                .isInstanceOf(ApiException.class);
    }
}
