package com.shopflow.reco.api;

import com.shopflow.common.dto.ApiResponse;
import com.shopflow.reco.domain.ProductNode;
import com.shopflow.reco.repository.RecommendationRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recommendation API. Read-only graph queries; results are eventually consistent
 * with orders (the graph is built asynchronously from order.events). Hot results
 * are good candidates for Redis caching to bound Neo4j traversal cost.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationRepository repository;

    public RecommendationController(RecommendationRepository repository) {
        this.repository = repository;
    }

    /** "Customers who bought this also bought…" for a product. */
    @GetMapping("/products/{productId}/also-bought")
    public ApiResponse<List<ProductNode>> alsoBought(
            @PathVariable String productId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(repository.alsoBought(productId, Math.min(limit, 50)));
    }

    /**
     * Personalised recommendations for a customer. Falls back to trending
     * products when the customer has no purchase history yet (cold-start).
     */
    @GetMapping("/customers/{customerId}")
    public ApiResponse<List<ProductNode>> forCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "10") int limit) {
        int capped = Math.min(limit, 50);
        List<ProductNode> recs = repository.recommendedForCustomer(customerId, capped);
        if (recs.isEmpty()) {
            recs = repository.trending(capped);
        }
        return ApiResponse.ok(recs);
    }

    /** Most-purchased products overall. */
    @GetMapping("/trending")
    public ApiResponse<List<ProductNode>> trending(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(repository.trending(Math.min(limit, 50)));
    }
}

