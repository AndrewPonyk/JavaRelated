package com.bank.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Fraud Detection Service Application
 *
 * ML-based fraud detection using CatBoost for real-time transaction scoring.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class FraudDetectionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionServiceApplication.class, args);
    }
}
