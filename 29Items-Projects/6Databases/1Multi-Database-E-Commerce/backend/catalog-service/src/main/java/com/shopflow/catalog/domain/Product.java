package com.shopflow.catalog.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Product catalog document (MongoDB). The flexible shape — nested variants and
 * an open {@code attributes} map — is exactly why the catalog lives in a
 * document store rather than relational tables: products across categories have
 * wildly different attribute sets.
 */
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed
    @NotBlank
    private String sku;

    @NotBlank
    private String name;
    private String description;

    @Indexed
    @NotBlank
    private String category;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;
    private String currency;
    private boolean active;

    @PositiveOrZero
    private int stockOnHand;

    /** Rolling rating aggregate, maintained from review.scored events. */
    private double averageRating;
    private int reviewCount;

    /** Free-form, category-specific attributes (e.g. {"ram":"16GB","color":"black"}). */
    private Map<String, Object> attributes;

    /** Purchasable variants (size/color/...). */
    private List<Variant> variants;

    private Instant updatedAt;

    @Version
    private Long mongoVersion;

    /** A purchasable variation of a product. */
    public record Variant(String variantSku, Map<String, String> options, BigDecimal priceDelta, int stock) { }

    // --- getters / setters (trimmed; setters omitted where immutable in practice) ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getStockOnHand() {
        return stockOnHand;
    }

    public void setStockOnHand(int stockOnHand) {
        this.stockOnHand = stockOnHand;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }
}
