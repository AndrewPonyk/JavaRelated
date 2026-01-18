package com.bank.transaction.model;

/**
 * Types of transactions supported by the platform.
 */
public enum TransactionType {
    INTERNAL_TRANSFER,    // Transfer within same bank
    EXTERNAL_TRANSFER,    // Transfer to external bank
    DEPOSIT,              // Cash/check deposit
    WITHDRAWAL,           // Cash withdrawal
    PAYMENT,              // Bill payment
    LOAN_DISBURSEMENT,    // Loan amount credited
    LOAN_REPAYMENT,       // Loan EMI payment
    INTEREST_CREDIT,      // Interest credited
    FEE_DEDUCTION         // Service fee
}
