package com.approval.workflow.verticles;

import com.approval.workflow.models.NlpAnalysisResult;
import com.approval.workflow.services.NlpClientService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker Verticle delegating document text processing to spaCy NLP microservice.
 */
public class NlpRoutingVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(NlpRoutingVerticle.class);

    private NlpClientService nlpClientService;

    @Override
    public void start(Promise<Void> startPromise) {
        this.nlpClientService = new NlpClientService(vertx, config());

        vertx.eventBus().consumer("nlp.analyze.request", this::handleAnalyzeRequest);
        log.info("NlpRoutingVerticle started and listening on 'nlp.analyze.request'");
        startPromise.complete();
    }

    private void handleAnalyzeRequest(Message<JsonObject> message) {
        JsonObject body = message.body();
        String title = body.getString("title", "");
        String content = body.getString("content", "");

        nlpClientService.analyzeDocument(title, content)
                .onSuccess(result -> {
                    message.reply(result.toJson());
                })
                .onFailure(err -> {
                    log.error("NLP analysis failed: {}", err.getMessage());
                    // Return fallback neutral
                    NlpAnalysisResult fallback = new NlpAnalysisResult();
                    fallback.setCategory("GENERAL");
                    fallback.setRecommendedRole("TEAM_LEAD");
                    fallback.setPolarity("NEUTRAL_FALLBACK");
                    message.reply(fallback.toJson());
                });
    }
}
