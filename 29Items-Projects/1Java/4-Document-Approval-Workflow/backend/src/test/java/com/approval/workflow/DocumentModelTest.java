package com.approval.workflow;

import com.approval.workflow.models.*;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentModelTest {

    @Test
    @DisplayName("Test Document JSON serialization and deserialization roundtrip")
    void testDocumentJsonRoundtrip() {
        Document doc = new Document();
        doc.setId("doc-12345");
        doc.setTitle("Enterprise Cloud SLA Contract");
        doc.setContent("Detailed agreement clauses and financial terms.");
        doc.setStatus(DocumentStatus.IN_REVIEW);
        doc.setCurrentTier(2);

        JsonObject creator = new JsonObject()
                .put("userId", "usr-01")
                .put("name", "Jane Doe")
                .put("role", "CREATOR");
        doc.setCreator(creator);

        // Steps
        ApprovalStep step1 = new ApprovalStep("step-1", 1, Role.TEAM_LEAD);
        step1.setStatus("APPROVED");
        step1.setAssignedTo("John Lead");
        step1.setActionTakenAt(Instant.now());
        step1.setComments("Approved initial phase.");

        ApprovalStep step2 = new ApprovalStep("step-2", 2, Role.LEGAL_COUNSEL);
        doc.setApprovalSteps(List.of(step1, step2));

        // SLA
        SlaRecord sla = new SlaRecord(Instant.now().plusSeconds(3600));
        sla.setBreached(false);
        doc.setSla(sla);

        // NLP
        NlpAnalysisResult nlp = new NlpAnalysisResult();
        nlp.setCategory("LEGAL_RISK");
        nlp.setSentimentScore(-0.35);
        nlp.setPolarity("NEGATIVE");
        nlp.setUrgencyScore(0.85);
        nlp.setRecommendedRole("LEGAL_COUNSEL");
        nlp.setExtractedEntities(List.of("Enterprise Cloud", "SLA"));
        doc.setNlpAnalysis(nlp);

        // Audit Trail
        doc.addAuditEntry("DOC_CREATED", "Jane Doe", "Created doc.");

        // Serialize to JSON
        JsonObject json = doc.toJson();
        assertNotNull(json);
        assertEquals("doc-12345", json.getString("id"));
        assertEquals("Enterprise Cloud SLA Contract", json.getString("title"));
        assertEquals("IN_REVIEW", json.getString("status"));
        assertEquals(2, json.getInteger("currentTier"));

        // Deserialize back
        Document restored = Document.fromJson(json);
        assertNotNull(restored);
        assertEquals(doc.getId(), restored.getId());
        assertEquals(doc.getTitle(), restored.getTitle());
        assertEquals(doc.getStatus(), restored.getStatus());
        assertEquals(doc.getCurrentTier(), restored.getCurrentTier());
        assertEquals(2, restored.getApprovalSteps().size());
        assertEquals(Role.LEGAL_COUNSEL, restored.getApprovalSteps().get(1).getRequiredRole());
        assertEquals("LEGAL_RISK", restored.getNlpAnalysis().getCategory());
        assertEquals(0.85, restored.getNlpAnalysis().getUrgencyScore());
        assertEquals(1, restored.getAuditTrail().size());
    }

    @Test
    @DisplayName("Test Role parsing utility fallback")
    void testRoleParsing() {
        assertEquals(Role.ADMIN, Role.fromString("ADMIN"));
        assertEquals(Role.LEGAL_COUNSEL, Role.fromString("legal_counsel"));
        assertEquals(Role.CREATOR, Role.fromString("UNKNOWN_ROLE_NAME"));
        assertEquals(Role.CREATOR, Role.fromString(null));
    }

    @Test
    @DisplayName("Test DocumentStatus terminal states")
    void testDocumentStatusTerminal() {
        assertTrue(DocumentStatus.APPROVED.isTerminal());
        assertTrue(DocumentStatus.REJECTED.isTerminal());
        assertFalse(DocumentStatus.IN_REVIEW.isTerminal());
        assertFalse(DocumentStatus.DRAFT.isTerminal());
        assertFalse(DocumentStatus.SLA_BREACHED.isTerminal());
    }
}
