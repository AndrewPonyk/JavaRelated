package com.bank.account.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event representing an account being unfrozen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUnfrozenEvent implements AccountEvent {

    private UUID eventId;
    private UUID accountId;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "ACCOUNT_UNFROZEN";
    }

    @Override
    public UUID getAggregateId() {
        return accountId;
    }
}
