package com.shopflow.catalog.service;

import com.shopflow.catalog.domain.Product;
import com.shopflow.catalog.domain.Review;
import com.shopflow.catalog.repository.ProductRepository;
import com.shopflow.catalog.repository.ReviewRepository;
import com.shopflow.common.error.ApiException;
import com.shopflow.common.event.ProductUpdatedEvent;
import com.shopflow.common.event.ReviewCreatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Catalog application service: product CRUD + search, and the reviews feature
 * (which drives the ML sentiment loop). Persists to MongoDB and announces
 * changes on Kafka so search/recommendation read-models and ml-service follow.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository products;
    private final ReviewRepository reviews;
    private final MongoTemplate mongo;
    private final KafkaTemplate<String, Object> kafka;

    public CatalogService(ProductRepository products, ReviewRepository reviews,
                          MongoTemplate mongo, KafkaTemplate<String, Object> kafka) {
        this.products = products;
        this.reviews = reviews;
        this.mongo = mongo;
        this.kafka = kafka;
    }

    // --- products ------------------------------------------------------------

    public Page<Product> list(String category, Pageable pageable) {
        return (category == null || category.isBlank())
                ? products.findByActiveIsTrue(pageable)
                : products.findByCategoryAndActiveIsTrue(category, pageable);
    }

    public Page<Product> search(String text, Pageable pageable) {
        return products.findByNameContainingIgnoreCaseAndActiveIsTrue(text, pageable);
    }

    public List<String> categories() {
        return mongo.findDistinct(new Query(), "category", Product.class, String.class);
    }

    public Product get(String id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product", id));
    }

    public Product upsert(Product product) {
        product.setUpdatedAt(Instant.now());
        Product saved = products.save(product);
        publishProduct(saved);
        return saved;
    }

    public void delete(String id) {
        Product product = get(id);
        product.setActive(false); // soft-delete keeps history + lets read-models react
        product.setUpdatedAt(Instant.now());
        products.save(product);
        publishProduct(product);
        log.info("Soft-deleted product {}", id);
    }

    // --- reviews (social + ML loop) ------------------------------------------

    public Page<Review> listReviews(String productId, Pageable pageable) {
        return reviews.findByProductIdOrderByCreatedAtDesc(productId, pageable);
    }

    /** Persist a review, update the product's star-rating aggregate, and emit an event for ML scoring. */
    public Review addReview(String productId, String author, int rating, String text) {
        Product product = get(productId); // 404 if missing
        Review review = reviews.save(new Review(productId, author, rating, text));

        // Running average of the numeric star rating (sentiment is filled async).
        int newCount = product.getReviewCount() + 1;
        double newAvg = ((product.getAverageRating() * product.getReviewCount()) + rating) / newCount;
        product.setReviewCount(newCount);
        product.setAverageRating(Math.round(newAvg * 100.0) / 100.0);
        products.save(product);

        kafka.send(ReviewCreatedEvent.TOPIC, productId, new ReviewCreatedEvent(
                UUID.randomUUID(), null, 1, Instant.now(),
                review.getId(), productId, author, rating, text));
        log.info("Added review {} for product {}", review.getId(), productId);
        return review;
    }

    /** Apply an ML sentiment result to a stored review (idempotent upsert). */
    public void applySentiment(String reviewId, String label, double score) {
        reviews.findById(reviewId).ifPresent(review -> {
            review.setSentimentLabel(label);
            review.setSentimentScore(score);
            reviews.save(review);
            log.debug("Applied sentiment {} to review {}", label, reviewId);
        });
    }

    // --- helpers -------------------------------------------------------------

    private void publishProduct(Product p) {
        kafka.send(ProductUpdatedEvent.TOPIC, p.getId(), new ProductUpdatedEvent(
                UUID.randomUUID(), null, 1, Instant.now(),
                p.getId(), p.getName(), p.getCategory(), p.getPrice(), p.isActive()));
        log.info("Published ProductUpdatedEvent for {}", p.getId());
    }
}
