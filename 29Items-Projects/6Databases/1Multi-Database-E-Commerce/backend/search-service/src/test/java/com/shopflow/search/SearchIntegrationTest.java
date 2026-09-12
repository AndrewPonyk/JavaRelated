package com.shopflow.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflow.search.domain.ProductDocument;
import com.shopflow.search.repository.ProductSearchRepository;
import com.shopflow.search.service.SearchService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test of {@link SearchService} against a real Elasticsearch
 * (Testcontainers). Exercises the full-text + category + price-range query. The
 * Kafka listener is disabled so the test focuses on the ES read-model.
 */
@SpringBootTest
@Testcontainers
class SearchIntegrationTest {

    @Container
    static final ElasticsearchContainer ES = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4"))
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + ES.getHttpHostAddress());
        registry.add("spring.kafka.listener.auto-startup", () -> "false"); // no broker in this test
    }

    @Autowired
    private SearchService searchService;

    @Autowired
    private ProductSearchRepository repository;

    @Autowired
    private ElasticsearchOperations operations;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new ProductDocument("s1", "Red Running Shoes", "shoes", new BigDecimal("50.00"), true),
                new ProductDocument("s2", "Blue Running Shoes", "shoes", new BigDecimal("80.00"), true),
                new ProductDocument("h1", "Red Wool Hat", "hats", new BigDecimal("20.00"), true)));
        operations.indexOps(ProductDocument.class).refresh();
    }

    @Test
    void fullTextSearch_matchesByName() {
        var result = searchService.search("Red", null, null, null, PageRequest.of(0, 10));
        assertThat(result.items()).extracting(ProductDocument::getId).containsExactlyInAnyOrder("s1", "h1");
    }

    @Test
    void categoryFilter_narrowsResults() {
        var result = searchService.search(null, "shoes", null, null, PageRequest.of(0, 10));
        assertThat(result.items()).extracting(ProductDocument::getId).containsExactlyInAnyOrder("s1", "s2");
    }

    @Test
    void priceRange_filtersResults() {
        var result = searchService.search(null, null, new BigDecimal("30"), new BigDecimal("60"),
                PageRequest.of(0, 10));
        assertThat(result.items()).extracting(ProductDocument::getId).containsExactly("s1");
        assertThat(result.total()).isEqualTo(1);
    }
}
