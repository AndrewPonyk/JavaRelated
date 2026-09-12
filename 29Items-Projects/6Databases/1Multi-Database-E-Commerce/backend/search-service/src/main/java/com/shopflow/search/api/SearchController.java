package com.shopflow.search.api;

import com.shopflow.common.dto.ApiResponse;
import com.shopflow.search.service.SearchService;
import com.shopflow.search.service.SearchService.SearchResult;
import java.math.BigDecimal;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search API backed entirely by the Elasticsearch read-model (never the catalog's
 * MongoDB). Eventually consistent with the catalog by design.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** Full-text product search with optional category + price-range facets. */
    @GetMapping
    public ApiResponse<SearchResult> search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(searchService.search(
                query, category, minPrice, maxPrice, PageRequest.of(page, Math.min(size, 50))));
    }
}
