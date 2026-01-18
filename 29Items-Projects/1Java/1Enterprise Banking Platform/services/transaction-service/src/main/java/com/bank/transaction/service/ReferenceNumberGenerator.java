package com.bank.transaction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique transaction reference numbers.
 * Format: TXN-YYYYMMDD-XXXXXX
 */
@Slf4j
@Component
public class ReferenceNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Generate a new unique reference number.
     *
     * @return the generated reference number
     */
    public Mono<String> generate() {
        return Mono.fromSupplier(() -> {
            String datePrefix = LocalDateTime.now().format(DATE_FORMAT);
            String randomSuffix = String.format("%06d", RANDOM.nextInt(1000000));

            String reference = "TXN-" + datePrefix + "-" + randomSuffix;
            log.debug("Generated reference number: {}", reference);

            return reference;
        });
    }
}
