package com.example.trading.infrastructure.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.trading.domain.AuditType;
import com.example.trading.domain.Trade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerRepositoryTest {

    @Test
    void storesTradesAndAuditEventsInSequence() {
        ConcurrentTradeRepository trades = new ConcurrentTradeRepository();
        Trade trade = new Trade(
                1L, 2L, 3L, "buyer", "seller", "AAPL", 1L,
                BigDecimal.TEN, Instant.EPOCH);
        trades.saveAll(List.of(trade));
        assertEquals(trade, trades.findById(1L).orElseThrow());
        assertEquals(List.of(trade), trades.findAll());
        assertThrows(IllegalArgumentException.class, () -> trades.saveAll(List.of(trade)));
        Trade second = new Trade(
                2L, 4L, 5L, "buyer2", "seller2", "MSFT", 1L,
                BigDecimal.TEN, Instant.EPOCH);
        assertThrows(IllegalArgumentException.class,
                () -> trades.saveAll(List.of(second, trade)));
        assertEquals(1, trades.findAll().size());
        assertTrue(trades.findById(2L).isEmpty());

        ConcurrentAuditLog audit = new ConcurrentAuditLog();
        audit.record(AuditType.TRADE_EXECUTED, "1", "quantity=1", Instant.EPOCH);
        audit.record(AuditType.ORDER_FILLED, "2", "executedQuantity=1", Instant.EPOCH);
        assertEquals(2, audit.findAll().size());
        assertEquals(1L, audit.findAll().getFirst().sequence());
    }
}
