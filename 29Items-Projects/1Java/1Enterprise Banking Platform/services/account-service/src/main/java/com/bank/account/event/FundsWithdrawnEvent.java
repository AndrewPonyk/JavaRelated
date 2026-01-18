package com.bank.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event representing funds withdrawn from an account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsWithdrawnEvent implements AccountEvent {

    private UUID eventId;
    private UUID accountId;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private String reference;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "FUNDS_WITHDRAWN";
    }

    @Override
    public UUID getAggregateId() {
        return accountId;
    }
}
