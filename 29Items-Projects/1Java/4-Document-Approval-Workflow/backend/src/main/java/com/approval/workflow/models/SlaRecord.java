package com.approval.workflow.models;

import io.vertx.core.json.JsonObject;
import java.time.Instant;

/**
 * Encapsulates SLA tracking attributes for multi-level workflow escalation.
 */
public class SlaRecord {
    private Instant deadline;
    private boolean breached;
    private String escalatedTo;
    private boolean warningSent;

    public SlaRecord() {
        this.breached = false;
        this.warningSent = false;
    }

    public SlaRecord(Instant deadline) {
        this.deadline = deadline;
        this.breached = false;
        this.warningSent = false;
    }

    public JsonObject toJson() {
        return new JsonObject()
                .put("deadline", deadline != null ? deadline.toString() : null)
                .put("breached", breached)
                .put("escalatedTo", escalatedTo)
                .put("warningSent", warningSent);
    }

    public static SlaRecord fromJson(JsonObject json) {
        if (json == null) return new SlaRecord();
        SlaRecord record = new SlaRecord();
        if (json.containsKey("deadline") && json.getValue("deadline") != null) {
            record.setDeadline(Document.parseInstant(json.getValue("deadline")));
        }
        record.setBreached(json.getBoolean("breached", false));
        record.setEscalatedTo(json.getString("escalatedTo"));
        record.setWarningSent(json.getBoolean("warningSent", false));
        return record;
    }

    // Getters and Setters
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }

    public boolean isBreached() { return breached; }
    public void setBreached(boolean breached) { this.breached = breached; }

    public String getEscalatedTo() { return escalatedTo; }
    public void setEscalatedTo(String escalatedTo) { this.escalatedTo = escalatedTo; }

    public boolean isWarningSent() { return warningSent; }
    public void setWarningSent(boolean warningSent) { this.warningSent = warningSent; }
}
