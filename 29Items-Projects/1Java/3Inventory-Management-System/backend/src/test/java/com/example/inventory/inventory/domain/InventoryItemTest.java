package com.example.inventory.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.inventory.common.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InventoryItemTest {
    private InventoryItem item;

    @BeforeEach
    void setUp() {
        item = new InventoryItem(new Warehouse("main", "Main"), "SKU-1", "CODE-1", "Widget", 20, 5);
    }

    @Test
    void supportsReceiptShipmentAdjustmentAndMetadata() {
        assertThat(item.receive(5)).isEqualTo(20);
        assertThat(item.ship(4)).isEqualTo(25);
        assertThat(item.adjust(-1)).isEqualTo(21);
        item.updateMetadata("CODE-2", "Updated", 7);
        assertThat(item.getQuantity()).isEqualTo(20);
        assertThat(item.getBarcode()).isEqualTo("CODE-2");
        assertThat(item.getName()).isEqualTo("Updated");
        assertThat(item.getReorderPoint()).isEqualTo(7);
    }

    @Test
    void reservesReleasesAndFulfillsStock() {
        item.reserve(8);
        assertThat(item.getReservedQuantity()).isEqualTo(8);
        assertThat(item.getAvailableQuantity()).isEqualTo(12);
        item.releaseReservation(3);
        assertThat(item.fulfillReservation(5)).isEqualTo(20);
        assertThat(item.getQuantity()).isEqualTo(15);
        assertThat(item.getReservedQuantity()).isZero();
    }

    @Test
    void rejectsInvalidStockTransitions() {
        item.reserve(12);
        assertThatThrownBy(() -> item.ship(9)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> item.adjust(-9)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> item.reserve(9)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> item.releaseReservation(13)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> item.fulfillReservation(13)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyDeactivatesEmptyItemsAndBlocksInactiveMutations() {
        assertThatThrownBy(item::deactivate).isInstanceOf(BusinessRuleException.class);
        item.adjust(-20);
        item.deactivate();
        assertThat(item.isActive()).isFalse();
        assertThatThrownBy(() -> item.receive(1)).isInstanceOf(BusinessRuleException.class);
        item.activate();
        item.receive(1);
        assertThat(item.isActive()).isTrue();
    }

    @Test
    void rejectsInvalidConstructionAndArithmetic() {
        Warehouse warehouse = new Warehouse("MAIN", "Main");
        assertThatThrownBy(() -> new InventoryItem(warehouse, "", "B", "N", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryItem(warehouse, "S", "B", "N", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.receive(0)).isInstanceOf(BusinessRuleException.class);
        item.adjust(Long.MAX_VALUE - 20);
        assertThatThrownBy(() -> item.adjust(1)).isInstanceOf(BusinessRuleException.class);
    }
}
