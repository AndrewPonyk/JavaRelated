package com.approval.workflow;

import com.approval.workflow.models.*;
import com.approval.workflow.services.DocumentService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class DocumentWorkflowTest {

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        JsonObject config = new JsonObject()
                .put("sla", new JsonObject()
                        .put("tier1Hours", 4)
                        .put("tier2Hours", 24));
        this.documentService = new DocumentService(config);
    }

    @Test
    @DisplayName("Test Document creation with High-Risk Legal NLP auto-routing")
    void testHighRiskLegalAutoRouting(Vertx vertx, VertxTestContext testContext) {
        JsonObject input = new JsonObject()
                .put("title", "Vendor breach and lawsuit claim")
                .put("content", "Immediate legal dispute arising from contract penalty violation.");

        NlpAnalysisResult nlpResult = new NlpAnalysisResult();
        nlpResult.setCategory("LEGAL_RISK");
        nlpResult.setUrgencyScore(0.9);
        nlpResult.setPolarity("CRITICAL");
        nlpResult.setRecommendedRole("LEGAL_COUNSEL");

        Document doc = documentService.buildNewDocument(input, nlpResult);

        assertNotNull(doc);
        assertEquals(DocumentStatus.IN_REVIEW, doc.getStatus());
        assertEquals(1, doc.getCurrentTier());
        assertNotNull(doc.getSla());
        assertFalse(doc.getSla().isBreached());

        // Verify routing tiers
        List<ApprovalStep> steps = doc.getApprovalSteps();
        assertEquals(2, steps.size());
        assertEquals(Role.LEGAL_COUNSEL, steps.get(0).getRequiredRole());
        assertEquals(Role.EXECUTIVE, steps.get(1).getRequiredRole());

        testContext.completeNow();
    }

    @Test
    @DisplayName("Test Multi-Level Approval State Progression")
    void testApprovalProgression(Vertx vertx, VertxTestContext testContext) {
        JsonObject input = new JsonObject()
                .put("title", "Quarterly Marketing Budget")
                .put("content", "General budget proposal for Q4 campaigns.");

        NlpAnalysisResult nlpResult = new NlpAnalysisResult();
        nlpResult.setCategory("GENERAL");
        nlpResult.setUrgencyScore(0.2);
        nlpResult.setPolarity("NEUTRAL");
        nlpResult.setRecommendedRole("TEAM_LEAD");

        Document doc = documentService.buildNewDocument(input, nlpResult);
        assertEquals(1, doc.getCurrentTier());

        // 1. Approve Tier 1
        boolean tier1Approved = documentService.processReviewerAction(
                doc, "APPROVE", "Sarah Lead", Role.TEAM_LEAD, "Looks good to me.");
        assertTrue(tier1Approved);
        assertEquals(2, doc.getCurrentTier());
        assertEquals(DocumentStatus.IN_REVIEW, doc.getStatus());

        // 2. Approve Tier 2 (Final Tier)
        boolean tier2Approved = documentService.processReviewerAction(
                doc, "APPROVE", "Mark Director", Role.DEPARTMENT_HEAD, "Final sign-off granted.");
        assertTrue(tier2Approved);
        assertEquals(DocumentStatus.APPROVED, doc.getStatus());

        testContext.completeNow();
    }

    @Test
    @DisplayName("Test Document Rejection Terminal State")
    void testRejection(Vertx vertx, VertxTestContext testContext) {
        JsonObject input = new JsonObject()
                .put("title", "Unauthorized Server Purchase")
                .put("content", "Hardware procurement without purchase order.");

        NlpAnalysisResult nlpResult = new NlpAnalysisResult();
        nlpResult.setCategory("FINANCIAL");
        nlpResult.setUrgencyScore(0.5);

        Document doc = documentService.buildNewDocument(input, nlpResult);
        boolean rejected = documentService.processReviewerAction(
                doc, "REJECT", "Alice Manager", Role.TEAM_LEAD, "Policy violation: PO missing.");

        assertTrue(rejected);
        assertEquals(DocumentStatus.REJECTED, doc.getStatus());
        assertTrue(doc.getStatus().isTerminal());

        testContext.completeNow();
    }
}
