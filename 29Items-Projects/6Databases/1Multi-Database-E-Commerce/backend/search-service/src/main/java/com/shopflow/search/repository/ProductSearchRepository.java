package com.shopflow.search.repository;

import com.shopflow.search.domain.ProductDocument;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Elasticsearch repository for {@link ProductDocument}. Derived query methods
 * compile to ES queries; for relevance tuning (boosting, function_score) use
 * {@code ElasticsearchOperations} with a hand-built query instead.
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    Page<ProductDocument> findByNameContainingAndActiveIsTrue(String text, Pageable pageable);

    List<ProductDocument> findByCategory(String category);
}
