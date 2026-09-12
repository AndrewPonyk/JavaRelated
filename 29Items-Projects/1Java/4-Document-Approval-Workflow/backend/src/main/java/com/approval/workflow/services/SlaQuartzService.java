package com.approval.workflow.services;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Manages Quartz Scheduler jobs for SLA deadline tracking and escalation events.
 */
public class SlaQuartzService {
    private static final Logger log = LoggerFactory.getLogger(SlaQuartzService.class);

    private final Vertx vertx;
    private Scheduler scheduler;

    public SlaQuartzService(Vertx vertx) {
        this.vertx = vertx;
    }

    public void start() throws SchedulerException {
        StdSchedulerFactory factory = new StdSchedulerFactory();
        this.scheduler = factory.getScheduler();
        // Pass Vert.x instance in Quartz context for job execution access
        this.scheduler.getContext().put("vertx", vertx);
        this.scheduler.start();
        log.info("Quartz SLA Scheduler started successfully.");
    }

    public void scheduleSlaJob(String documentId, int tier, Instant deadline) {
        try {
            JobDetail job = JobBuilder.newJob(SlaBreachJob.class)
                    .withIdentity("sla-job-" + documentId + "-tier-" + tier, "sla-group")
                    .usingJobData("documentId", documentId)
                    .usingJobData("tier", tier)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("sla-trigger-" + documentId + "-tier-" + tier, "sla-group")
                    .startAt(Date.from(deadline))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                    .build();

            scheduler.scheduleJob(job, trigger);
            log.info("Scheduled Quartz SLA breach check for documentId={} tier={} at {}", documentId, tier, deadline);
        } catch (SchedulerException e) {
            log.error("Failed to schedule Quartz SLA job for documentId={}", documentId, e);
        }
    }

    public void cancelSlaJob(String documentId, int tier) {
        try {
            JobKey jobKey = new JobKey("sla-job-" + documentId + "-tier-" + tier, "sla-group");
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Cancelled Quartz SLA job for documentId={} tier={}", documentId, tier);
            }
        } catch (SchedulerException e) {
            log.error("Failed to cancel Quartz SLA job for documentId={}", documentId, e);
        }
    }

    public void stop() {
        if (scheduler != null) {
            try {
                scheduler.shutdown(true);
                log.info("Quartz SLA Scheduler stopped.");
            } catch (SchedulerException e) {
                log.error("Error shutting down Quartz scheduler", e);
            }
        }
    }

    /**
     * Quartz Job executed when an SLA deadline is reached.
     */
    public static class SlaBreachJob implements Job {
        private static final Logger jobLog = LoggerFactory.getLogger(SlaBreachJob.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap data = context.getMergedJobDataMap();
            String documentId = data.getString("documentId");
            int tier = data.getInt("tier");

            try {
                Vertx vertx = (Vertx) context.getScheduler().getContext().get("vertx");
                if (vertx != null) {
                    jobLog.warn("SLA Deadline EXPIRED for documentId={}, tier={}. Publishing breach event.", documentId, tier);
                    JsonObject payload = new JsonObject()
                            .put("documentId", documentId)
                            .put("tier", tier)
                            .put("breachedAt", Instant.now().toString());

                    vertx.eventBus().send("workflow.sla.breached", payload);
                }
            } catch (SchedulerException e) {
                jobLog.error("Failed to retrieve Vert.x instance in SlaBreachJob", e);
            }
        }
    }
}
