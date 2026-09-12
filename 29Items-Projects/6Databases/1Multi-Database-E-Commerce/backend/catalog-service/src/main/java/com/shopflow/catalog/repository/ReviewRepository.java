package com.shopflow.catalog.repository;

import com.shopflow.catalog.domain.Review;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/** MongoDB repository for product {@link Review}s. */
public interface ReviewRepository extends MongoRepository<Review, String> {

    Page<Review> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);

    List<Review> findByProductId(String productId);

    long countByProductId(String productId);
}
