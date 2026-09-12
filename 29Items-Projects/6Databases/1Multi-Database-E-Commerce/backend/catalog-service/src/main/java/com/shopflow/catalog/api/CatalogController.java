package com.shopflow.catalog.api;

import com.shopflow.catalog.domain.Product;
import com.shopflow.catalog.domain.Review;
import com.shopflow.catalog.service.CatalogService;
import com.shopflow.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product catalog REST API: browse/search/detail (public), product upsert +
 * delete (admin), and product reviews (which trigger ML sentiment scoring).
 */
@RestController
@RequestMapping("/api/v1/products")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** Browse active products, optionally filtered by category. */
    @GetMapping
    public ApiResponse<Page<Product>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(catalog.list(category, PageRequest.of(page, Math.min(size, 100))));
    }

    /** Name search within the catalog (Mongo). Full relevance search is in search-service. */
    @GetMapping("/search")
    public ApiResponse<Page<Product>> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(catalog.search(query, PageRequest.of(page, Math.min(size, 100))));
    }

    /** Distinct product categories (for filter UIs). */
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(catalog.categories());
    }

    /** Product detail by id. */
    @GetMapping("/{id}")
    public ApiResponse<Product> get(@PathVariable String id) {
        return ApiResponse.ok(catalog.get(id));
    }

    /** Create or update a product (admin). Emits ProductUpdatedEvent. */
    @PostMapping
    public ResponseEntity<ApiResponse<Product>> upsert(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(catalog.upsert(product)));
    }

    /** Soft-delete (deactivate) a product (admin). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        catalog.delete(id);
        return ApiResponse.ok(null);
    }

    // --- reviews -------------------------------------------------------------

    /** List a product's reviews, newest first. */
    @GetMapping("/{id}/reviews")
    public ApiResponse<Page<Review>> listReviews(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(catalog.listReviews(id, PageRequest.of(page, Math.min(size, 100))));
    }

    /** Post a review for a product. Emits ReviewCreatedEvent → ml-service scores sentiment. */
    @PostMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<Review>> addReview(
            @PathVariable String id,
            @Valid @RequestBody ReviewRequest req) {
        Review review = catalog.addReview(id, req.author(), req.rating(), req.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(review));
    }

    /** Inbound review payload. */
    public record ReviewRequest(
            @NotBlank String author,
            @Min(1) @Max(5) int rating,
            @NotBlank String text) {
    }
}
