package com.shopflow.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflow.catalog.domain.Product;
import com.shopflow.catalog.domain.Review;
import com.shopflow.common.error.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test against a real MongoDB (Testcontainers). Verifies product
 * persistence and the review → rating-aggregate flow. Kafka is mocked so the
 * test stays focused on the datastore.
 */
@DataMongoTest
@Import(CatalogService.class)
@Testcontainers
class CatalogServiceIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private CatalogService catalog;

    private Product sampleProduct() {
        Product p = new Product();
        p.setSku("SKU-" + System.nanoTime());
        p.setName("Test Widget");
        p.setCategory("widgets");
        p.setPrice(new BigDecimal("19.99"));
        p.setCurrency("EUR");
        p.setActive(true);
        p.setStockOnHand(5);
        return p;
    }

    @Test
    void upsert_then_get_persistsProduct() {
        Product saved = catalog.upsert(sampleProduct());

        Product found = catalog.get(saved.getId());
        assertThat(found.getName()).isEqualTo("Test Widget");
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void get_missing_throwsNotFound() {
        assertThatThrownBy(() -> catalog.get("does-not-exist"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void addReview_updatesRatingAggregate() {
        Product product = catalog.upsert(sampleProduct());

        catalog.addReview(product.getId(), "alice", 4, "Solid product");
        catalog.addReview(product.getId(), "bob", 2, "Meh");

        Product refreshed = catalog.get(product.getId());
        assertThat(refreshed.getReviewCount()).isEqualTo(2);
        assertThat(refreshed.getAverageRating()).isEqualTo(3.0); // (4 + 2) / 2

        assertThat(catalog.listReviews(product.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2);
    }

    @Test
    void delete_softDeactivatesProduct() {
        Product product = catalog.upsert(sampleProduct());

        catalog.delete(product.getId());

        assertThat(catalog.get(product.getId()).isActive()).isFalse();
    }
}
