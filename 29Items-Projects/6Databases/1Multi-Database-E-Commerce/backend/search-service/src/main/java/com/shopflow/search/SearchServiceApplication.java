package com.shopflow.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Search service entry point. Maintains an Elasticsearch read-model of the
 * catalog by consuming {@code product.events}, and serves full-text + faceted
 * queries. It is a derived store — never a system of record.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.shopflow.search", "com.shopflow.common"})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
