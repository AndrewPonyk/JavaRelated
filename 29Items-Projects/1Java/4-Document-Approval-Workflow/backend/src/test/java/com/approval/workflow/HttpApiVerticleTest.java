package com.approval.workflow;

import com.approval.workflow.verticles.HttpApiVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
public class HttpApiVerticleTest {

    private int port = 8089;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        JsonObject config = new JsonObject()
                .put("http", new JsonObject().put("port", port).put("host", "127.0.0.1"));

        vertx.deployVerticle(new HttpApiVerticle(), new DeploymentOptions().setConfig(config))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Test /health endpoint returns UP")
    void testHealthEndpoint(Vertx vertx, VertxTestContext testContext) {
        WebClient client = WebClient.create(vertx);

        client.get(port, "127.0.0.1", "/health")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertNotNull(body);
                        assertEquals("UP", body.getString("status"));
                    });
                    testContext.completeNow();
                }));
    }
}
