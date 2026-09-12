package com.shopflow.reco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Recommendation service entry point. Builds a purchase graph in Neo4j from
 * {@code order.events} — {@code (:Customer)-[:BOUGHT]->(:Product)} — and answers
 * "customers who bought X also bought Y" via graph traversal.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.shopflow.reco", "com.shopflow.common"})
public class RecommendationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
