package com.approval.workflow.services;

import com.approval.workflow.models.NlpAnalysisResult;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reactive client for communicating with the Python spaCy microservice.
 * Wrapped with Circuit Breaker for resilience.
 */
public class NlpClientService {
    private static final Logger log = LoggerFactory.getLogger(NlpClientService.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final String nlpHost;
    private final int nlpPort;

    public NlpClientService(Vertx vertx, JsonObject config) {
        JsonObject nlpConfig = config.getJsonObject("nlp", new JsonObject());
        this.nlpHost = nlpConfig.getString("host", "localhost");
        this.nlpPort = nlpConfig.getInteger("port", 8000);
        int timeoutMs = nlpConfig.getInteger("timeoutMs", 3000);
        int maxFailures = nlpConfig.getInteger("circuitBreakerMaxFailures", 5);

        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout(timeoutMs)
                .setKeepAlive(true));

        this.circuitBreaker = CircuitBreaker.create("nlp-circuit-breaker", vertx,
                new CircuitBreakerOptions()
                        .setMaxFailures(maxFailures)
                        .setTimeout(timeoutMs)
                        .setFallbackOnFailure(true)
                        .setResetTimeout(10000));
    }

    public Future<NlpAnalysisResult> analyzeDocument(String title, String content) {
        return circuitBreaker.executeWithFallback(promise -> {
            JsonObject requestBody = new JsonObject()
                    .put("title", title)
                    .put("content", content);

            log.debug("Dispatching NLP analysis request to {}:{}", nlpHost, nlpPort);

            webClient.post(nlpPort, nlpHost, "/analyze")
                    .sendJsonObject(requestBody)
                    .onSuccess(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject responseJson = response.bodyAsJsonObject();
                            NlpAnalysisResult result = NlpAnalysisResult.fromJson(responseJson);
                            promise.complete(result);
                        } else {
                            log.warn("NLP Service returned non-200 status: {}", response.statusCode());
                            promise.fail("NLP Service Error: " + response.statusMessage());
                        }
                    })
                    .onFailure(err -> {
                        log.error("Failed to connect to NLP service: {}", err.getMessage());
                        promise.fail(err);
                    });
        }, v -> fallbackAnalysis(title, content));
    }

    private NlpAnalysisResult fallbackAnalysis(String title, String content) {
        log.info("Circuit breaker triggered: Executing local fallback rule-based NLP analysis");
        String text = (title + " " + content).toLowerCase();
        NlpAnalysisResult fallback = new NlpAnalysisResult();
        
        if (text.contains("legal") || text.contains("lawsuit") || text.contains("contract") || text.contains("dispute")) {
            fallback.setCategory("LEGAL_RISK");
            fallback.setRecommendedRole("LEGAL_COUNSEL");
            fallback.setUrgencyScore(0.8);
        } else if (text.contains("budget") || text.contains("invoice") || text.contains("cost")) {
            fallback.setCategory("FINANCIAL");
            fallback.setRecommendedRole("FINANCE_CONTROLLER");
            fallback.setUrgencyScore(0.5);
        } else {
            fallback.setCategory("GENERAL");
            fallback.setRecommendedRole("TEAM_LEAD");
            fallback.setUrgencyScore(0.2);
        }
        
        fallback.setSentimentScore(0.0);
        fallback.setPolarity("NEUTRAL_FALLBACK");
        return fallback;
    }
}
