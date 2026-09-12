package com.example.inventory.forecast;

import com.example.inventory.inventory.domain.InventoryItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "forecast_snapshots")
public class ForecastSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem item;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "predicted_demand", nullable = false, precision = 18, scale = 4)
    private BigDecimal predictedDemand;

    @Column(name = "lower_bound", nullable = false, precision = 18, scale = 4)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", nullable = false, precision = 18, scale = 4)
    private BigDecimal upperBound;

    @Column(name = "model_version", nullable = false, length = 120)
    private String modelVersion;

    @Column(name = "feature_version", nullable = false, length = 80)
    private String featureVersion;

    @Column(name = "model_mae", precision = 18, scale = 4)
    private BigDecimal modelMae;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ForecastSnapshot() {
    }

    public ForecastSnapshot(InventoryItem item, int horizonDays, BigDecimal predictedDemand,
                            BigDecimal lowerBound, BigDecimal upperBound, String modelVersion,
                            String featureVersion, BigDecimal modelMae, Instant generatedAt) {
        this.item = item;
        this.horizonDays = horizonDays;
        this.predictedDemand = predictedDemand;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.modelVersion = modelVersion;
        this.featureVersion = featureVersion;
        this.modelMae = modelMae;
        this.generatedAt = generatedAt;
    }

    public UUID getId() { return id; }
    public InventoryItem getItem() { return item; }
    public int getHorizonDays() { return horizonDays; }
    public BigDecimal getPredictedDemand() { return predictedDemand; }
    public BigDecimal getLowerBound() { return lowerBound; }
    public BigDecimal getUpperBound() { return upperBound; }
    public String getModelVersion() { return modelVersion; }
    public String getFeatureVersion() { return featureVersion; }
    public BigDecimal getModelMae() { return modelMae; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

