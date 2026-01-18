package com.bank.account.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Account domain entity representing a bank account.
 * Uses event sourcing for state management.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("accounts")
public class Account {

    @Id
    private UUID id;

    @Column("account_number")
    private String accountNumber;

    @Column("owner_name")
    private String ownerName;

    @Column("owner_email")
    private String ownerEmail;

    @Column("account_type")
    private AccountType accountType;

    @Column("currency")
    private String currency;

    @Column("balance")
    private BigDecimal balance;

    @Column("status")
    private AccountStatus status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    /**
     * Apply a debit (withdrawal) to the account.
     *
     * @param amount the amount to debit
     * @throws IllegalStateException if insufficient funds
     */
    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Apply a credit (deposit) to the account.
     *
     * @param amount the amount to credit
     */
    public void credit(BigDecimal amount) {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Freeze the account preventing any transactions.
     */
    public void freeze() {
        this.status = AccountStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    /**
     * Unfreeze a frozen account.
     */
    public void unfreeze() {
        if (this.status != AccountStatus.FROZEN) {
            throw new IllegalStateException("Account is not frozen");
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Close the account permanently.
     */
    public void close() {
        if (this.balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account balance must be zero to close");
        }
        this.status = AccountStatus.CLOSED;
        this.closedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
