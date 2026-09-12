package com.example.inventory.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.inventory.common.error.BusinessRuleException;
import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.domain.Warehouse;
import org.junit.jupiter.api.Test;

class ReservationTest {
    @Test
    void supportsOneTerminalTransition() {
        InventoryItem item = new InventoryItem(new Warehouse("MAIN", "Main"), "SKU", "CODE", "Item", 5, 1);
        Reservation released = new Reservation(item, 1, "ORDER-1", "Cancelled", "user");
        assertThat(released.getItem()).isSameAs(item);
        assertThat(released.getQuantity()).isEqualTo(1);
        assertThat(released.getExternalReference()).isEqualTo("ORDER-1");
        assertThat(released.getReason()).isEqualTo("Cancelled");
        assertThat(released.getActor()).isEqualTo("user");
        assertThat(released.getVersion()).isZero();
        assertThat(released.getId()).isNull();
        assertThat(released.getCreatedAt()).isNull();
        assertThat(released.getUpdatedAt()).isNull();
        released.release();
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThatThrownBy(released::release).isInstanceOf(BusinessRuleException.class);

        Reservation fulfilled = new Reservation(item, 1, "ORDER-2", "Ship", "user");
        fulfilled.fulfill();
        assertThat(fulfilled.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
    }
}
