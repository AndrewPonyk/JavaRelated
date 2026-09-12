package com.example.inventory.alert;

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
@Table(name = "low_stock_alerts")
public class LowStockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem item;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reorder_point", nullable = false)
    private long reorderPoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AlertStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 160)
    private String acknowledgedBy;

    protected LowStockAlert() {
    }

    public LowStockAlert(UUID eventId, InventoryItem item, long quantity, long reorderPoint) {
        this.eventId = eventId;
        this.item = item;
        this.quantity = quantity;
        this.reorderPoint = reorderPoint;
        this.status = AlertStatus.OPEN;
    }

    public void acknowledge(String actor) {
        if (status != AlertStatus.OPEN) {
            return;
        }
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedAt = Instant.now();
        acknowledgedBy = actor;
    }

    public void resolve() {
        if (status == AlertStatus.OPEN) {
            status = AlertStatus.RESOLVED;
        }
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public InventoryItem getItem() { return item; }
    public long getQuantity() { return quantity; }
    public long getReorderPoint() { return reorderPoint; }
    public AlertStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
}
