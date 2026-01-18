package com.bank.account.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique account numbers.
 * Format: YYMM-XXXX-XXXX where X is a random digit.
 */
@Slf4j
@Component
public class AccountNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMM");

    /**
     * Generate a new unique account number.
     *
     * @return the generated account number
     */
    public Mono<String> generate() {
        return Mono.fromSupplier(() -> {
            String datePrefix = LocalDate.now().format(DATE_FORMAT);
            String randomPart1 = String.format("%04d", RANDOM.nextInt(10000));
            String randomPart2 = String.format("%04d", RANDOM.nextInt(10000));

            String accountNumber = datePrefix + "-" + randomPart1 + "-" + randomPart2;
            log.debug("Generated account number: {}", accountNumber);

            return accountNumber;
        });
    }
}
