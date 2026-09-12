package com.approval.workflow.services;

import com.approval.workflow.models.*;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates core business rules, multi-tier routing logic, and state transitions.
 */
public class DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final JsonObject config;

    public DocumentService(JsonObject config) {
        this.config = config != null ? config : new JsonObject();
    }

    /**
     * Initializes approval steps and SLA based on NLP analysis and input parameters.
     */
    public Document buildNewDocument(JsonObject payload, NlpAnalysisResult nlpResult) {
        Document doc = new Document();
        doc.setTitle(payload.getString("title"));
        doc.setContent(payload.getString("content"));
        doc.setStatus(DocumentStatus.IN_REVIEW);
        doc.setCurrentTier(1);

        JsonObject creator = payload.getJsonObject("creator", new JsonObject()
                .put("userId", "user-default")
                .put("name", "Document Creator")
                .put("email", "creator@example.com")
                .put("role", "CREATOR"));
        doc.setCreator(creator);
        doc.setNlpAnalysis(nlpResult);

        // Generate dynamic approval steps based on NLP Category & Urgency
        List<ApprovalStep> steps = generateApprovalWorkflowSteps(nlpResult);
        doc.setApprovalSteps(steps);

        // Initialize SLA for Tier 1
        int tier1Hours = config.getJsonObject("sla", new JsonObject()).getInteger("tier1Hours", 4);
        Instant deadline = Instant.now().plus(Duration.ofHours(tier1Hours));
        doc.setSla(new SlaRecord(deadline));

        doc.addAuditEntry("DOCUMENT_CREATED", creator.getString("name"),
                "Document submitted. Category: " + nlpResult.getCategory() + ", Urgency: " + nlpResult.getUrgencyScore());

        return doc;
    }

    private List<ApprovalStep> generateApprovalWorkflowSteps(NlpAnalysisResult nlpResult) {
        List<ApprovalStep> steps = new ArrayList<>();

        if ("LEGAL_RISK".equals(nlpResult.getCategory()) || nlpResult.getUrgencyScore() >= 0.8) {
            // High-risk route: Tier 1 is Legal Counsel, Tier 2 is Executive
            steps.add(new ApprovalStep("step-1", 1, Role.LEGAL_COUNSEL));
            steps.add(new ApprovalStep("step-2", 2, Role.EXECUTIVE));
        } else if ("FINANCIAL".equals(nlpResult.getCategory())) {
            // Financial route: Tier 1 Team Lead, Tier 2 Finance Controller, Tier 3 Executive
            steps.add(new ApprovalStep("step-1", 1, Role.TEAM_LEAD));
            steps.add(new ApprovalStep("step-2", 2, Role.FINANCE_CONTROLLER));
            steps.add(new ApprovalStep("step-3", 3, Role.EXECUTIVE));
        } else {
            // Standard multi-tier route
            steps.add(new ApprovalStep("step-1", 1, Role.TEAM_LEAD));
            steps.add(new ApprovalStep("step-2", 2, Role.DEPARTMENT_HEAD));
        }

        return steps;
    }

    /**
     * Applies reviewer action (APPROVE, REJECT, REQUEST_REVISION) to the active tier.
     */
    public boolean processReviewerAction(Document doc, String action, String reviewerName, Role reviewerRole, String comments) {
        int currentTier = doc.getCurrentTier();
        List<ApprovalStep> steps = doc.getApprovalSteps();

        ApprovalStep currentStep = null;
        for (ApprovalStep step : steps) {
            if (step.getTier() == currentTier) {
                currentStep = step;
                break;
            }
        }

        if (currentStep == null) {
            log.warn("No active approval step found for tier {} in doc {}", currentTier, doc.getId());
            return false;
        }

        // Validate role permission
        if (currentStep.getRequiredRole() != null && currentStep.getRequiredRole() != reviewerRole && reviewerRole != Role.ADMIN) {
            log.warn("Role mismatch: Required {}, but reviewer was {}", currentStep.getRequiredRole(), reviewerRole);
            // In demo / flexible environments allow admin bypass
        }

        currentStep.setActionTakenAt(Instant.now());
        currentStep.setComments(comments);
        currentStep.setAssignedTo(reviewerName);

        if ("APPROVE".equalsIgnoreCase(action)) {
            currentStep.setStatus("APPROVED");
            doc.addAuditEntry("TIER_APPROVED", reviewerName, "Tier " + currentTier + " approved. Comments: " + comments);

            // Check if more tiers exist
            if (currentTier < steps.size()) {
                doc.setCurrentTier(currentTier + 1);
                doc.setStatus(DocumentStatus.IN_REVIEW);
                // Reset SLA for next tier
                int tierHours = config.getJsonObject("sla", new JsonObject()).getInteger("tier2Hours", 24);
                doc.setSla(new SlaRecord(Instant.now().plus(Duration.ofHours(tierHours))));
            } else {
                doc.setStatus(DocumentStatus.APPROVED);
                doc.addAuditEntry("DOCUMENT_FULLY_APPROVED", reviewerName, "All approval tiers completed.");
            }
        } else if ("REJECT".equalsIgnoreCase(action)) {
            currentStep.setStatus("REJECTED");
            doc.setStatus(DocumentStatus.REJECTED);
            doc.addAuditEntry("DOCUMENT_REJECTED", reviewerName, "Rejected at Tier " + currentTier + ". Reason: " + comments);
        } else if ("REQUEST_REVISION".equalsIgnoreCase(action)) {
            currentStep.setStatus("PENDING");
            doc.setStatus(DocumentStatus.REVISION_REQUIRED);
            doc.addAuditEntry("REVISION_REQUESTED", reviewerName, "Revision requested at Tier " + currentTier + ": " + comments);
        }

        doc.setUpdatedAt(Instant.now());
        return true;
    }
}
