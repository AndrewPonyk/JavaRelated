package com.bank.transaction.dto;

import com.bank.transaction.model.TransactionStatus;
import com.bank.transaction.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for transaction information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private UUID id;
    private String referenceNumber;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private String description;
    private TransactionStatus status;
    private Instant initiatedAt;
    private Instant completedAt;
    private String failureReason;
}
