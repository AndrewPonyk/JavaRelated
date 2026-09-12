package com.example.inventory.inventory.domain;

import com.example.inventory.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 128)
    private String barcode;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Column(name = "reorder_point", nullable = false)
    private long reorderPoint;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    public InventoryItem(Warehouse warehouse, String sku, String barcode, String name,
                         long quantity, long reorderPoint) {
        this.warehouse = warehouse;
        this.sku = required(sku, "sku");
        this.barcode = required(barcode, "barcode");
        this.name = required(name, "name");
        setQuantity(quantity);
        setReorderPoint(reorderPoint);
        this.active = true;
    }

    public void updateMetadata(String barcode, String name, long reorderPoint) {
        this.barcode = required(barcode, "barcode");
        this.name = required(name, "name");
        setReorderPoint(reorderPoint);
    }

    public long adjust(long delta) {
        requireActive();
        long before = quantity;
        long next;
        try {
            next = Math.addExact(quantity, delta);
        } catch (ArithmeticException exception) {
            throw new BusinessRuleException("Quantity exceeds the supported range.");
        }
        if (next < reservedQuantity) {
            throw new BusinessRuleException("Adjustment would reduce on-hand stock below reserved stock.");
        }
        setQuantity(next);
        return before;
    }

    public long receive(long amount) {
        requirePositive(amount);
        return adjust(amount);
    }

    public long ship(long amount) {
        requirePositive(amount);
        if (amount > getAvailableQuantity()) {
            throw new BusinessRuleException("Insufficient available stock for shipment.");
        }
        return adjust(-amount);
    }

    public void reserve(long amount) {
        requireActive();
        requirePositive(amount);
        if (amount > getAvailableQuantity()) {
            throw new BusinessRuleException("Insufficient available stock for reservation.");
        }
        reservedQuantity = Math.addExact(reservedQuantity, amount);
    }

    public void releaseReservation(long amount) {
        requirePositive(amount);
        if (amount > reservedQuantity) {
            throw new BusinessRuleException("Reservation quantity exceeds reserved stock.");
        }
        reservedQuantity -= amount;
    }

    public long fulfillReservation(long amount) {
        requirePositive(amount);
        if (amount > reservedQuantity) {
            throw new BusinessRuleException("Reservation quantity exceeds reserved stock.");
        }
        long before = quantity;
        reservedQuantity -= amount;
        quantity -= amount;
        return before;
    }

    public void deactivate() {
        if (quantity != 0 || reservedQuantity != 0) {
            throw new BusinessRuleException("Only an empty item with no reservations can be deactivated.");
        }
        active = false;
    }

    public void activate() {
        active = true;
    }

    private void requireActive() {
        if (!active) {
            throw new BusinessRuleException("Inventory item is inactive.");
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new BusinessRuleException("Quantity must be greater than zero.");
        }
    }

    private void setQuantity(long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }
        this.quantity = quantity;
    }

    private void setReorderPoint(long reorderPoint) {
        if (reorderPoint < 0) {
            throw new IllegalArgumentException("reorderPoint cannot be negative");
        }
        this.reorderPoint = reorderPoint;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public long getQuantity() {
        return quantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public long getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public long getReorderPoint() {
        return reorderPoint;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
