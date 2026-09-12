package com.approval.workflow;

import com.approval.workflow.config.AppConfig;
import com.approval.workflow.verticles.*;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point and orchestrator for the reactive Document Approval application.
 */
public class MainVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle())
                .onSuccess(id -> log.info("Application deployed successfully with ID: {}", id))
                .onFailure(err -> log.error("Failed to deploy MainVerticle: {}", err.getMessage(), err));
    }

    @Override
    public void start(Promise<Void> startPromise) {
        log.info("Bootstrapping Document Approval Workflow Reactive Engine...");

        AppConfig.load(vertx)
                .compose(this::deployAllVerticles)
                .onSuccess(v -> {
                    log.info("All Document Approval Verticles deployed and running successfully.");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    log.error("Fatal: Verticle deployment sequence failed: {}", err.getMessage(), err);
                    startPromise.fail(err);
                });
    }

    private Future<Void> deployAllVerticles(JsonObject config) {
        DeploymentOptions standardOptions = new DeploymentOptions().setConfig(config);
        DeploymentOptions workerOptions = new DeploymentOptions().setConfig(config).setWorker(true);

        // 1. Deploy DatabaseVerticle
        return vertx.deployVerticle(new DatabaseVerticle(), standardOptions)
                .compose(dbId -> {
                    log.info("DatabaseVerticle deployed [{}]", dbId);
                    // 2. Deploy NlpRoutingVerticle
                    return vertx.deployVerticle(new NlpRoutingVerticle(), standardOptions);
                })
                .compose(nlpId -> {
                    log.info("NlpRoutingVerticle deployed [{}]", nlpId);
                    // 3. Deploy SlaSchedulerVerticle (Worker verticle for Quartz interactions)
                    return vertx.deployVerticle(new SlaSchedulerVerticle(), workerOptions);
                })
                .compose(slaId -> {
                    log.info("SlaSchedulerVerticle deployed [{}]", slaId);
                    // 4. Deploy DocumentWorkflowVerticle
                    return vertx.deployVerticle(new DocumentWorkflowVerticle(), standardOptions);
                })
                .compose(wfId -> {
                    log.info("DocumentWorkflowVerticle deployed [{}]", wfId);
                    // 5. Deploy HttpApiVerticle (HTTP Gateway)
                    return vertx.deployVerticle(new HttpApiVerticle(), standardOptions);
                })
                .mapEmpty();
    }
}
