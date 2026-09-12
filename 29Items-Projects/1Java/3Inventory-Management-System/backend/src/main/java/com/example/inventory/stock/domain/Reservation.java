package com.example.inventory.stock.domain;

import com.example.inventory.common.error.BusinessRuleException;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem item;

    @Column(nullable = false)
    private long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReservationStatus status;

    @Column(name = "external_reference", nullable = false, unique = true, length = 128)
    private String externalReference;

    @Column(nullable = false, length = 250)
    private String reason;

    @Column(nullable = false, length = 160)
    private String actor;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(InventoryItem item, long quantity, String externalReference,
                       String reason, String actor) {
        this.item = item;
        this.quantity = quantity;
        this.externalReference = externalReference;
        this.reason = reason;
        this.actor = actor;
        this.status = ReservationStatus.ACTIVE;
    }

    public void release() {
        requireActive();
        status = ReservationStatus.RELEASED;
    }

    public void fulfill() {
        requireActive();
        status = ReservationStatus.FULFILLED;
    }

    private void requireActive() {
        if (status != ReservationStatus.ACTIVE) {
            throw new BusinessRuleException("Reservation is no longer active.");
        }
    }

    public UUID getId() { return id; }
    public InventoryItem getItem() { return item; }
    public long getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
