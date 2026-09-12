package com.approval.workflow.handlers;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Centralized failure handler formatting RFC 7807 problem details.
 */
public class ErrorHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        int statusCode = ctx.statusCode() > 0 ? ctx.statusCode() : 500;
        String message = failure != null ? failure.getMessage() : "Internal Server Error";

        if (statusCode == 500 && failure != null) {
            log.error("Unhandled server exception at {}: {}", ctx.request().path(), failure.getMessage(), failure);
        } else {
            log.warn("Handled client error at {} [status={}]: {}", ctx.request().path(), statusCode, message);
        }

        JsonObject errorResponse = new JsonObject()
                .put("status", statusCode)
                .put("error", getErrorType(statusCode))
                .put("message", message)
                .put("path", ctx.request().path())
                .put("timestamp", Instant.now().toString());

        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(errorResponse.encode());
    }

    private String getErrorType(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            default -> "Internal Server Error";
        };
    }
}
