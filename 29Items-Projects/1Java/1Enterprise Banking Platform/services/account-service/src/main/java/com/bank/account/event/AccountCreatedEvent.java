package com.bank.account.event;

import com.bank.account.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event representing account creation for event sourcing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent implements AccountEvent {

    private UUID eventId;
    private UUID accountId;
    private String accountNumber;
    private String ownerEmail;
    private AccountType accountType;
    private String currency;
    private BigDecimal initialBalance;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return "ACCOUNT_CREATED";
    }

    @Override
    public UUID getAggregateId() {
        return accountId;
    }
}
