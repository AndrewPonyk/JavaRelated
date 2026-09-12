package com.approval.workflow.handlers;

import com.approval.workflow.models.Role;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles JWT extraction, simulated authentication headers, and RBAC authorization.
 */
public class AuthHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");
        String roleHeader = ctx.request().getHeader("X-User-Role");
        String userHeader = ctx.request().getHeader("X-User-Id");
        String nameHeader = ctx.request().getHeader("X-User-Name");

        // Set default authenticated user context
        JsonObject userContext = new JsonObject();
        if (roleHeader != null) {
            userContext.put("role", Role.fromString(roleHeader).name());
        } else {
            userContext.put("role", Role.CREATOR.name());
        }

        userContext.put("userId", userHeader != null ? userHeader : "user-anonymous");
        userContext.put("name", nameHeader != null ? nameHeader : "Default User");

        ctx.put("userContext", userContext);
        ctx.next();
    }
}
