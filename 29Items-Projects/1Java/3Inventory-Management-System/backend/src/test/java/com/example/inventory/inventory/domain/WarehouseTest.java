package com.example.inventory.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WarehouseTest {
    @Test
    void normalizesAndUpdatesWarehouse() {
        Warehouse warehouse = new Warehouse(" main ", " Main warehouse ");
        assertThat(warehouse.getCode()).isEqualTo("MAIN");
        assertThat(warehouse.getId()).isNull();
        assertThat(warehouse.getVersion()).isZero();
        assertThat(warehouse.getCreatedAt()).isNull();
        assertThat(warehouse.getUpdatedAt()).isNull();
        warehouse.update("Renamed");
        warehouse.deactivate();
        assertThat(warehouse.getName()).isEqualTo("Renamed");
        assertThat(warehouse.isActive()).isFalse();
        warehouse.activate();
        assertThat(warehouse.isActive()).isTrue();
    }

    @Test
    void requiresCodeAndName() {
        assertThatThrownBy(() -> new Warehouse("", "name")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Warehouse("code", " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
