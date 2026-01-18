package com.bank.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event representing funds deposited into an account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundsDepositedEvent implements AccountEvent {

    private UUID eventId;
    private UUID accountId;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private String reference;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "FUNDS_DEPOSITED";
    }

    @Override
    public UUID getAggregateId() {
        return accountId;
    }
}
