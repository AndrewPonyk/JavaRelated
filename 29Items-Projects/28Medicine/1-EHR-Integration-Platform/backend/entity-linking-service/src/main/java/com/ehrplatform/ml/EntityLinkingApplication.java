package com.ehrplatform.ml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entity Linking Service.
 *
 * <p>Performs ML-based entity linking (resolving records that refer to the same
 * real-world patient/concept) and free risk stratification by finding similar
 * clinical notes (kNN over note embeddings). Orchestrates candidate generation
 * here and delegates heavy model inference to a dedicated Python server.
 */
@SpringBootApplication
public class EntityLinkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityLinkingApplication.class, args);
    }
}
