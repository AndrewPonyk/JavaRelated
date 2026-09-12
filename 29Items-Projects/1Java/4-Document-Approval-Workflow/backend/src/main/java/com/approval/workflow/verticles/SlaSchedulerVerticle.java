package com.approval.workflow.verticles;

import com.approval.workflow.services.SlaQuartzService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Verticle managing SLA timer registrations with Quartz Scheduler.
 */
public class SlaSchedulerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(SlaSchedulerVerticle.class);

    private SlaQuartzService slaQuartzService;

    @Override
    public void start(Promise<Void> startPromise) {
        this.slaQuartzService = new SlaQuartzService(vertx);
        try {
            this.slaQuartzService.start();
        } catch (SchedulerException e) {
            log.error("Failed to start Quartz SLA scheduler", e);
            startPromise.fail(e);
            return;
        }

        vertx.eventBus().consumer("sla.schedule.timer", this::handleScheduleTimer);
        vertx.eventBus().consumer("sla.cancel.timer", this::handleCancelTimer);

        log.info("SlaSchedulerVerticle registered and listening for SLA triggers.");
        startPromise.complete();
    }

    private void handleScheduleTimer(Message<JsonObject> message) {
        JsonObject body = message.body();
        String documentId = body.getString("documentId");
        int tier = body.getInteger("tier", 1);
        String deadlineStr = body.getString("deadline");

        if (documentId != null && deadlineStr != null) {
            Instant deadline = Instant.parse(deadlineStr);
            slaQuartzService.scheduleSlaJob(documentId, tier, deadline);
            message.reply(new JsonObject().put("status", "SCHEDULED"));
        } else {
            message.fail(400, "Missing documentId or deadline");
        }
    }

    private void handleCancelTimer(Message<JsonObject> message) {
        JsonObject body = message.body();
        String documentId = body.getString("documentId");
        int tier = body.getInteger("tier", 1);

        if (documentId != null) {
            slaQuartzService.cancelSlaJob(documentId, tier);
            message.reply(new JsonObject().put("status", "CANCELLED"));
        }
    }

    @Override
    public void stop() {
        if (slaQuartzService != null) {
            slaQuartzService.stop();
        }
    }
}
