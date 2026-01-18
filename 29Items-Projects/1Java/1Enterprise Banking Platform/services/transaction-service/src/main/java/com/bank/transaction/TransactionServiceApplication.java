package com.bank.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Transaction Service Application
 *
 * Handles transaction processing with CQRS pattern.
 * Implements event sourcing for transaction audit trail.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableR2dbcRepositories
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
