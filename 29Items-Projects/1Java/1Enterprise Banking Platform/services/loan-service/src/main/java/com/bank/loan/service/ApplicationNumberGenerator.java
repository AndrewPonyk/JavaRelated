package com.bank.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique loan application numbers.
 * Format: LA-YYYYMMDD-XXXXXX
 */
@Slf4j
@Component
public class ApplicationNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public Mono<String> generate() {
        return Mono.fromSupplier(() -> {
            String datePrefix = LocalDate.now().format(DATE_FORMAT);
            String randomPart = String.format("%06d", RANDOM.nextInt(1000000));
            String applicationNumber = "LA-" + datePrefix + "-" + randomPart;
            log.debug("Generated application number: {}", applicationNumber);
            return applicationNumber;
        });
    }
}
