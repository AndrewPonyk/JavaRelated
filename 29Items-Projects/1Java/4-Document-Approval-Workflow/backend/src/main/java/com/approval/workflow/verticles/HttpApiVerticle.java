package com.approval.workflow.verticles;

import com.approval.workflow.handlers.AuthHandler;
import com.approval.workflow.handlers.DocumentHandler;
import com.approval.workflow.handlers.ErrorHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * HTTP REST & WebSocket/SSE API Gateway Verticle.
 */
public class HttpApiVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HttpApiVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject httpConfig = config().getJsonObject("http", new JsonObject());
        int port = httpConfig.getInteger("port", 8080);
        String host = httpConfig.getString("host", "0.0.0.0");

        Router router = Router.router(vertx);

        // CORS Configuration
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");
        allowedHeaders.add("Authorization");
        allowedHeaders.add("X-User-Role");
        allowedHeaders.add("X-User-Id");
        allowedHeaders.add("X-User-Name");

        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);
        allowedMethods.add(HttpMethod.PUT);
        allowedMethods.add(HttpMethod.DELETE);
        allowedMethods.add(HttpMethod.OPTIONS);

        router.route().handler(CorsHandler.create()
                .addRelativeOrigin(".*")
                .allowedHeaders(allowedHeaders)
                .allowedMethods(allowedMethods));

        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        // Health Checks
        router.get("/health").handler(ctx -> {
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("status", "UP").put("engine", "Vert.x 4.5.3").encode());
        });

        // API Subrouter
        Router apiRouter = Router.router(vertx);
        apiRouter.route().handler(new AuthHandler());

        DocumentHandler docHandler = new DocumentHandler(vertx);
        apiRouter.post("/documents").handler(docHandler::createDocument);
        apiRouter.get("/documents").handler(docHandler::listDocuments);
        apiRouter.get("/documents/:id").handler(docHandler::getDocument);
        apiRouter.post("/documents/:id/action").handler(docHandler::processAction);
        apiRouter.get("/events/stream").handler(docHandler::streamLiveEvents);

        router.route("/api/v1/*").subRouter(apiRouter);

        // Centralized Error Router
        router.route().failureHandler(new ErrorHandler());

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port, host)
                .onSuccess(server -> {
                    log.info("HttpApiVerticle started and listening on http://{}:{}", host, port);
                    startPromise.complete();
                })
                .onFailure(err -> {
                    log.error("Failed to start HTTP server on port {}: {}", port, err.getMessage());
                    startPromise.fail(err);
                });
    }
}
