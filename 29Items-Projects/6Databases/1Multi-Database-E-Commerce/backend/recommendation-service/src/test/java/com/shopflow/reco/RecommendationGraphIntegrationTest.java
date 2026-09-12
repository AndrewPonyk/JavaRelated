package com.shopflow.reco;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflow.common.event.OrderPlacedEvent;
import com.shopflow.reco.domain.ProductNode;
import com.shopflow.reco.messaging.OrderEventConsumer;
import com.shopflow.reco.repository.RecommendationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test of the collaborative-filtering graph against a real Neo4j
 * (Testcontainers). Feeds purchase events through the consumer, then verifies
 * the "also bought", personalised, and trending queries.
 */
@DataNeo4jTest
@Import(OrderEventConsumer.class)
@Testcontainers
class RecommendationGraphIntegrationTest {

    @Container
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>(
            DockerImageName.parse("neo4j:5-community"));

    @DynamicPropertySource
    static void neo4jProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", NEO4J::getAdminPassword);
    }

    @Autowired
    private OrderEventConsumer consumer;

    @Autowired
    private RecommendationRepository repository;

    private void buy(String customer, String... productIds) {
        List<OrderPlacedEvent.Line> lines = java.util.Arrays.stream(productIds)
                .map(p -> new OrderPlacedEvent.Line(p, 1, new BigDecimal("10.00")))
                .toList();
        consumer.onOrderPlaced(new OrderPlacedEvent(
                UUID.randomUUID(), null, 1, Instant.now(),
                "order-" + UUID.randomUUID(), customer, new BigDecimal("10.00"), "EUR", lines));
    }

    @Test
    void buildsGraph_andComputesRecommendations() {
        // alice bought A,B ; bob bought A,C  -> A links B and C via shared shoppers
        buy("alice", "A", "B");
        buy("bob", "A", "C");

        List<ProductNode> alsoBoughtA = repository.alsoBought("A", 10);
        assertThat(alsoBoughtA).extracting(ProductNode::getId).contains("B", "C");

        // alice shares A with bob; bob also bought C which alice hasn't -> recommend C
        List<ProductNode> forAlice = repository.recommendedForCustomer("alice", 10);
        assertThat(forAlice).extracting(ProductNode::getId).contains("C");

        // A was bought by both -> most trending
        List<ProductNode> trending = repository.trending(10);
        assertThat(trending).isNotEmpty();
        assertThat(trending.get(0).getId()).isEqualTo("A");
    }
}
