package com.shopflow.search.service;

import com.shopflow.search.domain.ProductDocument;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

/**
 * Full-text + faceted product search over the Elasticsearch read-model. Builds a
 * portable {@link CriteriaQuery} combining a free-text match on the name with
 * optional category and price-range filters; only active products are returned.
 */
@Service
public class SearchService {

    private final ElasticsearchOperations operations;

    public SearchService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public SearchResult search(String text, String category,
                               BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Criteria criteria = new Criteria("active").is(true);
        if (text != null && !text.isBlank()) {
            criteria = criteria.and(new Criteria("name").matches(text));
        }
        if (category != null && !category.isBlank()) {
            criteria = criteria.and(new Criteria("category").is(category));
        }
        if (minPrice != null) {
            criteria = criteria.and(new Criteria("price").greaterThanEqual(minPrice));
        }
        if (maxPrice != null) {
            criteria = criteria.and(new Criteria("price").lessThanEqual(maxPrice));
        }

        CriteriaQuery query = new CriteriaQuery(criteria).setPageable(pageable);
        SearchHits<ProductDocument> hits = operations.search(query, ProductDocument.class);

        List<ProductDocument> items = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        return new SearchResult(items, hits.getTotalHits(), pageable.getPageNumber(), pageable.getPageSize());
    }

    /** Paged search result with total count. */
    public record SearchResult(List<ProductDocument> items, long total, int page, int size) { }
}
