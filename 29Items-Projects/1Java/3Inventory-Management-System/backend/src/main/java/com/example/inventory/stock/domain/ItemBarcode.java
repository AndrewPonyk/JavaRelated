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
@Table(name = "item_barcodes")
public class ItemBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem item;

    @Column(nullable = false, unique = true, length = 128)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BarcodeSymbology symbology;

    @Column(name = "primary_barcode", nullable = false)
    private boolean primary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ItemBarcode() {
    }

    public ItemBarcode(InventoryItem item, String barcode, BarcodeSymbology symbology, boolean primary) {
        this.item = item;
        this.barcode = normalize(barcode);
        this.symbology = symbology;
        this.primary = primary;
    }

    public void makePrimary() {
        this.primary = true;
    }

    public void makeAlias() {
        this.primary = false;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("barcode is required");
        }
        return value.trim().toUpperCase();
    }

    public UUID getId() {
        return id;
    }

    public InventoryItem getItem() {
        return item;
    }

    public String getBarcode() {
        return barcode;
    }

    public BarcodeSymbology getSymbology() {
        return symbology;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

