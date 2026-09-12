package com.shopflow.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Catalog service entry point. Owns the product catalog in MongoDB and emits
 * {@code ProductUpdatedEvent} so search and recommendation read-models stay
 * in sync.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.shopflow.catalog", "com.shopflow.common"})
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
