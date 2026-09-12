package com.approval.workflow.handlers;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles HTTP REST endpoints for Document workflow operations.
 * Communicates asynchronously with Verticles over the EventBus.
 */
public class DocumentHandler {
    private static final Logger log = LoggerFactory.getLogger(DocumentHandler.class);

    private final Vertx vertx;

    public DocumentHandler(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * POST /api/v1/documents - Ingest and auto-route new document
     */
    public void createDocument(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null || !body.containsKey("title") || !body.containsKey("content")) {
            ctx.fail(400, new IllegalArgumentException("Title and content are required."));
            return;
        }

        JsonObject userContext = ctx.get("userContext");
        if (userContext != null) {
            body.put("creator", userContext);
        }

        vertx.eventBus().<JsonObject>request("workflow.document.create", body, new DeliveryOptions().setSendTimeout(5000))
                .onSuccess(reply -> {
                    ctx.response()
                            .setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .end(reply.body().encode());
                })
                .onFailure(ctx::fail);
    }

    /**
     * GET /api/v1/documents - List documents with optional status/role filter
     */
    public void listDocuments(RoutingContext ctx) {
        String status = ctx.request().getParam("status");
        String role = ctx.request().getParam("role");

        JsonObject query = new JsonObject();
        if (status != null && !status.isBlank()) query.put("status", status);
        if (role != null && !role.isBlank()) query.put("role", role);

        vertx.eventBus().<JsonObject>request("db.document.find", query)
                .onSuccess(reply -> {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(reply.body().encode());
                })
                .onFailure(ctx::fail);
    }

    /**
     * GET /api/v1/documents/:id - Get specific document by ID
     */
    public void getDocument(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        JsonObject query = new JsonObject().put("id", id);

        vertx.eventBus().<JsonObject>request("db.document.getById", query)
                .onSuccess(reply -> {
                    JsonObject doc = reply.body();
                    if (doc == null || doc.isEmpty()) {
                        ctx.fail(404, new RuntimeException("Document not found"));
                    } else {
                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(doc.encode());
                    }
                })
                .onFailure(ctx::fail);
    }

    /**
     * POST /api/v1/documents/:id/action - Reviewer decision (APPROVE, REJECT, REQUEST_REVISION)
     */
    public void processAction(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        JsonObject body = ctx.body().asJsonObject();

        if (body == null || !body.containsKey("action")) {
            ctx.fail(400, new IllegalArgumentException("Action is required (APPROVE, REJECT, REQUEST_REVISION)."));
            return;
        }

        JsonObject userContext = ctx.get("userContext");
        JsonObject actionPayload = new JsonObject()
                .put("documentId", id)
                .put("action", body.getString("action"))
                .put("comments", body.getString("comments", ""))
                .put("reviewer", userContext != null ? userContext : new JsonObject());

        vertx.eventBus().<JsonObject>request("workflow.document.action", actionPayload)
                .onSuccess(reply -> {
                    ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(reply.body().encode());
                })
                .onFailure(ctx::fail);
    }

    /**
     * GET /api/v1/events/stream - Server-Sent Events (SSE) live updates
     */
    public void streamLiveEvents(RoutingContext ctx) {
        HttpServerResponse response = ctx.response();
        response.putHeader("Content-Type", "text/event-stream")
                .putHeader("Cache-Control", "no-cache")
                .putHeader("Connection", "keep-alive")
                .setChunked(true);

        response.write("event: connected\ndata: {\"status\":\"CONNECTED\"}\n\n");

        var consumer = vertx.eventBus().<JsonObject>consumer("workflow.events.stream", message -> {
            response.write("event: workflow_update\ndata: " + message.body().encode() + "\n\n");
        });

        response.closeHandler(v -> {
            log.debug("SSE client disconnected, unregistering consumer");
            consumer.unregister();
        });
    }
}
