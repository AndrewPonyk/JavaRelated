package com.shopflow.catalog.repository;

import com.shopflow.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link Product}. Query methods are derived
 * into MongoDB queries; add {@code @Query} for richer aggregation pipelines.
 */
public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByCategoryAndActiveIsTrue(String category, Pageable pageable);

    Page<Product> findByActiveIsTrue(Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndActiveIsTrue(String text, Pageable pageable);
}
