package com.bank.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event representing an account being frozen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountFrozenEvent implements AccountEvent {

    private UUID eventId;
    private UUID accountId;
    private String reason;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "ACCOUNT_FROZEN";
    }

    @Override
    public UUID getAggregateId() {
        return accountId;
    }
}
