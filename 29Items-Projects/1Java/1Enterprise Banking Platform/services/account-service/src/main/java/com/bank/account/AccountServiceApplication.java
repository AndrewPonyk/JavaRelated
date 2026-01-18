package com.bank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Account Service Application
 *
 * Handles account management operations with event sourcing pattern.
 * Provides reactive APIs for account CRUD operations.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableR2dbcRepositories
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
