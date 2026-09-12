package com.example.inventory.stock.domain;

import com.example.inventory.inventory.domain.InventoryItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_item_id")
    private InventoryItem relatedItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private MovementType type;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(name = "quantity_before", nullable = false)
    private long quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private long quantityAfter;

    @Column(nullable = false, length = 250)
    private String reason;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockMovement() {
    }

    public StockMovement(InventoryItem item, InventoryItem relatedItem, MovementType type,
                         long quantityDelta, long quantityBefore, long quantityAfter,
                         String reason, String referenceType, String referenceId,
                         String actor, String idempotencyKey) {
        this.item = item;
        this.relatedItem = relatedItem;
        this.type = type;
        this.quantityDelta = quantityDelta;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.reason = reason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.actor = actor;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public InventoryItem getItem() { return item; }
    public InventoryItem getRelatedItem() { return relatedItem; }
    public MovementType getType() { return type; }
    public long getQuantityDelta() { return quantityDelta; }
    public long getQuantityBefore() { return quantityBefore; }
    public long getQuantityAfter() { return quantityAfter; }
    public String getReason() { return reason; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getActor() { return actor; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getOccurredAt() { return occurredAt; }
}

