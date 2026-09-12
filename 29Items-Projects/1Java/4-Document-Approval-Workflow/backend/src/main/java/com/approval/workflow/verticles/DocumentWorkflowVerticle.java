package com.approval.workflow.verticles;

import com.approval.workflow.models.*;
import com.approval.workflow.services.DocumentService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Core Workflow Engine Verticle coordinating multi-level state transitions,
 * NLP routing decisions, SLA management, and real-time live event broadcasts.
 */
public class DocumentWorkflowVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(DocumentWorkflowVerticle.class);

    private DocumentService documentService;

    @Override
    public void start(Promise<Void> startPromise) {
        this.documentService = new DocumentService(config());

        vertx.eventBus().consumer("workflow.document.create", this::handleCreateDocument);
        vertx.eventBus().consumer("workflow.document.action", this::handleReviewerAction);
        vertx.eventBus().consumer("workflow.sla.breached", this::handleSlaBreach);

        log.info("DocumentWorkflowVerticle registered and listening for workflow commands.");
        startPromise.complete();
    }

    private void handleCreateDocument(Message<JsonObject> message) {
        JsonObject payload = message.body();
        String title = payload.getString("title");
        String content = payload.getString("content");

        // 1. Request NLP Analysis & Auto-Routing from NlpRoutingVerticle
        JsonObject nlpReq = new JsonObject().put("title", title).put("content", content);
        vertx.eventBus().<JsonObject>request("nlp.analyze.request", nlpReq)
                .onSuccess(nlpReply -> {
                    NlpAnalysisResult nlpResult = NlpAnalysisResult.fromJson(nlpReply.body());
                    Document newDoc = documentService.buildNewDocument(payload, nlpResult);
                    String docId = "doc-" + UUID.randomUUID().toString().substring(0, 8);
                    newDoc.setId(docId);

                    // 2. Persist Document to MongoDB
                    vertx.eventBus().<JsonObject>request("db.document.save", newDoc.toJson())
                            .onSuccess(dbReply -> {
                                // 3. Schedule SLA Trigger in Quartz
                                if (newDoc.getSla() != null && newDoc.getSla().getDeadline() != null) {
                                    JsonObject slaReq = new JsonObject()
                                            .put("documentId", docId)
                                            .put("tier", newDoc.getCurrentTier())
                                            .put("deadline", newDoc.getSla().getDeadline().toString());
                                    vertx.eventBus().send("sla.schedule.timer", slaReq);
                                }

                                // 4. Broadcast Real-time Event to Connected Clients
                                vertx.eventBus().publish("workflow.events.stream", new JsonObject()
                                        .put("eventType", "DOCUMENT_CREATED")
                                        .put("document", newDoc.toJson()));

                                message.reply(newDoc.toJson());
                            })
                            .onFailure(err -> message.fail(500, "Database Error: " + err.getMessage()));
                })
                .onFailure(err -> message.fail(500, "NLP Routing Error: " + err.getMessage()));
    }

    private void handleReviewerAction(Message<JsonObject> message) {
        JsonObject body = message.body();
        String documentId = body.getString("documentId");
        String action = body.getString("action");
        String comments = body.getString("comments", "");
        JsonObject reviewer = body.getJsonObject("reviewer", new JsonObject());
        String reviewerName = reviewer.getString("name", "Reviewer");
        Role reviewerRole = Role.fromString(reviewer.getString("role"));

        // 1. Retrieve Current Document from DB
        vertx.eventBus().<JsonObject>request("db.document.getById", new JsonObject().put("id", documentId))
                .onSuccess(dbReply -> {
                    JsonObject docJson = dbReply.body();
                    if (docJson == null || docJson.isEmpty()) {
                        message.fail(404, "Document not found: " + documentId);
                        return;
                    }

                    Document doc = Document.fromJson(docJson);
                    int oldTier = doc.getCurrentTier();

                    // 2. Apply Business Logic & State Transition
                    boolean success = documentService.processReviewerAction(doc, action, reviewerName, reviewerRole, comments);
                    if (!success) {
                        message.fail(400, "Invalid state transition or tier not found");
                        return;
                    }

                    // 3. Cancel Old SLA Timer
                    vertx.eventBus().send("sla.cancel.timer", new JsonObject().put("documentId", documentId).put("tier", oldTier));

                    // 4. If advanced to next tier, schedule new SLA
                    if (doc.getStatus() == DocumentStatus.IN_REVIEW && doc.getSla() != null) {
                        JsonObject slaReq = new JsonObject()
                                .put("documentId", documentId)
                                .put("tier", doc.getCurrentTier())
                                .put("deadline", doc.getSla().getDeadline().toString());
                        vertx.eventBus().send("sla.schedule.timer", slaReq);
                    }

                    // 5. Persist Updated Document
                    vertx.eventBus().<JsonObject>request("db.document.update", doc.toJson())
                            .onSuccess(updateReply -> {
                                // 6. Broadcast Real-Time Update
                                vertx.eventBus().publish("workflow.events.stream", new JsonObject()
                                        .put("eventType", "DOCUMENT_UPDATED")
                                        .put("action", action)
                                        .put("document", doc.toJson()));

                                message.reply(doc.toJson());
                            })
                            .onFailure(err -> message.fail(500, "Database update failed: " + err.getMessage()));
                })
                .onFailure(err -> message.fail(500, "Failed to retrieve document: " + err.getMessage()));
    }

    private void handleSlaBreach(Message<JsonObject> message) {
        JsonObject breachData = message.body();
        String documentId = breachData.getString("documentId");
        int tier = breachData.getInteger("tier");

        log.warn("Processing SLA breach event for docId={}, tier={}", documentId, tier);

        vertx.eventBus().<JsonObject>request("db.document.getById", new JsonObject().put("id", documentId))
                .onSuccess(dbReply -> {
                    JsonObject docJson = dbReply.body();
                    if (docJson != null && !docJson.isEmpty()) {
                        Document doc = Document.fromJson(docJson);
                        if (doc.getStatus() == DocumentStatus.IN_REVIEW && doc.getCurrentTier() == tier) {
                            if (doc.getSla() != null) {
                                doc.getSla().setBreached(true);
                                doc.getSla().setEscalatedTo("EXECUTIVE_ESCALATION_QUEUE");
                            }
                            doc.setStatus(DocumentStatus.SLA_BREACHED);
                            doc.addAuditEntry("SLA_BREACHED", "SYSTEM_QUARTZ",
                                    "SLA deadline expired for Tier " + tier + ". Auto-escalated.");

                            vertx.eventBus().request("db.document.update", doc.toJson())
                                    .onSuccess(v -> {
                                        vertx.eventBus().publish("workflow.events.stream", new JsonObject()
                                                .put("eventType", "SLA_BREACHED")
                                                .put("document", doc.toJson()));
                                    });
                        }
                    }
                });
    }
}
