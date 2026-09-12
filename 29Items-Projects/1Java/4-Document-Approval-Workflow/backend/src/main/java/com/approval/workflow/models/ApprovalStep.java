package com.approval.workflow.models;

import io.vertx.core.json.JsonObject;
import java.time.Instant;

/**
 * Represents a discrete approval tier node in the document routing chain.
 */
public class ApprovalStep {
    private String stepId;
    private int tier;
    private Role requiredRole;
    private String assignedTo;
    private String status; // PENDING, APPROVED, REJECTED, SKIPPED
    private Instant actionTakenAt;
    private String comments;

    public ApprovalStep() {
        this.status = "PENDING";
    }

    public ApprovalStep(String stepId, int tier, Role requiredRole) {
        this.stepId = stepId;
        this.tier = tier;
        this.requiredRole = requiredRole;
        this.status = "PENDING";
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("stepId", stepId)
                .put("tier", tier)
                .put("requiredRole", requiredRole != null ? requiredRole.name() : null)
                .put("assignedTo", assignedTo)
                .put("status", status)
                .put("actionTakenAt", actionTakenAt != null ? actionTakenAt.toString() : null)
                .put("comments", comments);
        return json;
    }

    public static ApprovalStep fromJson(JsonObject json) {
        ApprovalStep step = new ApprovalStep();
        step.setStepId(json.getString("stepId"));
        step.setTier(json.getInteger("tier", 1));
        step.setRequiredRole(Role.fromString(json.getString("requiredRole")));
        step.setAssignedTo(json.getString("assignedTo"));
        step.setStatus(json.getString("status", "PENDING"));
        if (json.containsKey("actionTakenAt") && json.getValue("actionTakenAt") != null) {
            step.setActionTakenAt(Document.parseInstant(json.getValue("actionTakenAt")));
        }
        step.setComments(json.getString("comments"));
        return step;
    }

    // Getters and Setters
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }

    public Role getRequiredRole() { return requiredRole; }
    public void setRequiredRole(Role requiredRole) { this.requiredRole = requiredRole; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getActionTakenAt() { return actionTakenAt; }
    public void setActionTakenAt(Instant actionTakenAt) { this.actionTakenAt = actionTakenAt; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}
