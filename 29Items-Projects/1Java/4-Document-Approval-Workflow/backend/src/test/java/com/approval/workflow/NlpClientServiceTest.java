package com.approval.workflow;

import com.approval.workflow.models.NlpAnalysisResult;
import com.approval.workflow.services.NlpClientService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class NlpClientServiceTest {

    @Test
    @DisplayName("Test NLP Circuit Breaker fallback when external service is offline")
    void testCircuitBreakerFallback(Vertx vertx, VertxTestContext testContext) {
        // Point to an unused port to simulate offline service
        JsonObject config = new JsonObject().put("nlp", new JsonObject()
                .put("host", "127.0.0.1")
                .put("port", 59999)
                .put("timeoutMs", 500)
                .put("circuitBreakerMaxFailures", 1));

        NlpClientService nlpService = new NlpClientService(vertx, config);

        nlpService.analyzeDocument("Urgent lawsuit contract dispute", "Immediate legal breach liability penalty.")
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        assertNotNull(result);
                        assertEquals("LEGAL_RISK", result.getCategory());
                        assertEquals("LEGAL_COUNSEL", result.getRecommendedRole());
                        assertEquals("NEUTRAL_FALLBACK", result.getPolarity());
                        assertEquals(0.8, result.getUrgencyScore());
                    });
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Test Financial keyword fallback")
    void testFinancialFallback(Vertx vertx, VertxTestContext testContext) {
        JsonObject config = new JsonObject().put("nlp", new JsonObject()
                .put("host", "127.0.0.1")
                .put("port", 59999)
                .put("timeoutMs", 500));

        NlpClientService nlpService = new NlpClientService(vertx, config);

        nlpService.analyzeDocument("Q4 Budget Invoice", "Review hardware cost.")
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        assertNotNull(result);
                        assertEquals("FINANCIAL", result.getCategory());
                        assertEquals("FINANCE_CONTROLLER", result.getRecommendedRole());
                    });
                    testContext.completeNow();
                }));
    }
}
