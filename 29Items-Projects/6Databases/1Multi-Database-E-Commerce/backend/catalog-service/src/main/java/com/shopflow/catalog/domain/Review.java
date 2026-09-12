package com.shopflow.catalog.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer review of a product (MongoDB). Sentiment fields start null and are
 * filled asynchronously when ml-service returns a {@code ReviewScoredEvent}.
 */
@Document(collection = "reviews")
public class Review {

    @Id
    private String id;

    @Indexed
    private String productId;

    private String author;
    private int rating;
    private String text;

    private String sentimentLabel;   // POSITIVE | NEGATIVE | NEUTRAL (async)
    private Double sentimentScore;    // 0..1 (async)

    private Instant createdAt;

    public Review() {
    }

    public Review(String productId, String author, int rating, String text) {
        this.productId = productId;
        this.author = author;
        this.rating = rating;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public String getAuthor() {
        return author;
    }

    public int getRating() {
        return rating;
    }

    public String getText() {
        return text;
    }

    public String getSentimentLabel() {
        return sentimentLabel;
    }

    public void setSentimentLabel(String sentimentLabel) {
        this.sentimentLabel = sentimentLabel;
    }

    public Double getSentimentScore() {
        return sentimentScore;
    }

    public void setSentimentScore(Double sentimentScore) {
        this.sentimentScore = sentimentScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
