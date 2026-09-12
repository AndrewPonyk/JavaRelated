package com.approval.workflow.models;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate Root model representing a Document in the approval lifecycle.
 */
public class Document {
    private String id;
    private String title;
    private String content;
    private DocumentStatus status;
    private JsonObject creator;
    private int currentTier;
    private List<ApprovalStep> approvalSteps;
    private SlaRecord sla;
    private NlpAnalysisResult nlpAnalysis;
    private List<JsonObject> auditTrail;
    private Instant createdAt;
    private Instant updatedAt;

    public Document() {
        this.status = DocumentStatus.DRAFT;
        this.currentTier = 1;
        this.approvalSteps = new ArrayList<>();
        this.auditTrail = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (id != null) {
            json.put("_id", id);
            json.put("id", id);
        }
        json.put("title", title)
            .put("content", content)
            .put("status", status != null ? status.name() : DocumentStatus.DRAFT.name())
            .put("creator", creator)
            .put("currentTier", currentTier)
            .put("createdAt", createdAt != null ? createdAt.toString() : null)
            .put("updatedAt", updatedAt != null ? updatedAt.toString() : null);

        if (sla != null) {
            json.put("sla", sla.toJson());
        }
        if (nlpAnalysis != null) {
            json.put("nlpAnalysis", nlpAnalysis.toJson());
        }

        JsonArray stepsArray = new JsonArray();
        if (approvalSteps != null) {
            for (ApprovalStep step : approvalSteps) {
                stepsArray.add(step.toJson());
            }
        }
        json.put("approvalSteps", stepsArray);

        JsonArray auditArray = new JsonArray();
        if (auditTrail != null) {
            for (JsonObject entry : auditTrail) {
                auditArray.add(entry);
            }
        }
        json.put("auditTrail", auditArray);

        return json;
    }

    public static Document fromJson(JsonObject json) {
        if (json == null) return null;
        Document doc = new Document();
        doc.setId(parseId(json));
        doc.setTitle(json.getString("title"));
        doc.setContent(json.getString("content"));
        
        String statusStr = json.getString("status");
        if (statusStr != null) {
            try {
                doc.setStatus(DocumentStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                doc.setStatus(DocumentStatus.DRAFT);
            }
        }
        
        doc.setCreator(json.getJsonObject("creator"));
        doc.setCurrentTier(json.getInteger("currentTier", 1));

        doc.setCreatedAt(parseInstant(json.getValue("createdAt")));
        doc.setUpdatedAt(parseInstant(json.getValue("updatedAt")));

        if (json.containsKey("sla") && json.getJsonObject("sla") != null) {
            doc.setSla(SlaRecord.fromJson(json.getJsonObject("sla")));
        }
        if (json.containsKey("nlpAnalysis") && json.getJsonObject("nlpAnalysis") != null) {
            doc.setNlpAnalysis(NlpAnalysisResult.fromJson(json.getJsonObject("nlpAnalysis")));
        }

        JsonArray stepsArray = json.getJsonArray("approvalSteps");
        if (stepsArray != null) {
            List<ApprovalStep> steps = new ArrayList<>();
            for (int i = 0; i < stepsArray.size(); i++) {
                steps.add(ApprovalStep.fromJson(stepsArray.getJsonObject(i)));
            }
            doc.setApprovalSteps(steps);
        }

        JsonArray auditArray = json.getJsonArray("auditTrail");
        if (auditArray != null) {
            List<JsonObject> audit = new ArrayList<>();
            for (int i = 0; i < auditArray.size(); i++) {
                audit.add(auditArray.getJsonObject(i));
            }
            doc.setAuditTrail(audit);
        }

        return doc;
    }

    public static String parseId(JsonObject json) {
        if (json == null) return null;
        Object idVal = json.getValue("id");
        if (idVal == null) idVal = json.getValue("_id");
        if (idVal == null) return null;
        if (idVal instanceof JsonObject) {
            JsonObject obj = (JsonObject) idVal;
            if (obj.containsKey("$oid")) return obj.getString("$oid");
        }
        String s = idVal.toString();
        if (s.startsWith("{") && s.contains("$oid")) {
            try {
                JsonObject parsed = new JsonObject(s);
                if (parsed.containsKey("$oid")) return parsed.getString("$oid");
            } catch (Exception ignored) {}
        }
        return s;
    }

    public static Instant parseInstant(Object val) {
        if (val == null) return Instant.now();
        if (val instanceof String) {
            try {
                return Instant.parse((String) val);
            } catch (Exception e) {
                return Instant.now();
            }
        }
        if (val instanceof JsonObject) {
            JsonObject obj = (JsonObject) val;
            if (obj.containsKey("$date")) {
                try {
                    return Instant.parse(obj.getString("$date"));
                } catch (Exception e) {
                    return Instant.now();
                }
            }
        }
        return Instant.now();
    }

    public void addAuditEntry(String action, String performedBy, String details) {
        if (this.auditTrail == null) {
            this.auditTrail = new ArrayList<>();
        }
        JsonObject entry = new JsonObject()
                .put("action", action)
                .put("performedBy", performedBy)
                .put("timestamp", Instant.now().toString())
                .put("details", details);
        this.auditTrail.add(entry);
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public JsonObject getCreator() { return creator; }
    public void setCreator(JsonObject creator) { this.creator = creator; }

    public int getCurrentTier() { return currentTier; }
    public void setCurrentTier(int currentTier) { this.currentTier = currentTier; }

    public List<ApprovalStep> getApprovalSteps() { return approvalSteps; }
    public void setApprovalSteps(List<ApprovalStep> approvalSteps) { this.approvalSteps = approvalSteps; }

    public SlaRecord getSla() { return sla; }
    public void setSla(SlaRecord sla) { this.sla = sla; }

    public NlpAnalysisResult getNlpAnalysis() { return nlpAnalysis; }
    public void setNlpAnalysis(NlpAnalysisResult nlpAnalysis) { this.nlpAnalysis = nlpAnalysis; }

    public List<JsonObject> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<JsonObject> auditTrail) { this.auditTrail = auditTrail; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
