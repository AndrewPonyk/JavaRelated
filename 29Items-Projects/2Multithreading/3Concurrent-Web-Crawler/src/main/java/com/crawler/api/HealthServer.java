package com.crawler.api;

import com.crawler.core.CrawlerEngine;
import com.crawler.ml.ContentIndexer;
import com.crawler.util.CrawlMetrics;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server for health checks and API endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /health - Health check for Railway</li>
 *   <li>GET /status - Detailed crawler status</li>
 *   <li>GET /metrics - Crawl metrics as JSON</li>
 *   <li>GET /search?q=query - Search indexed content</li>
 * </ul>
 */
public class HealthServer { // |su:99 HTTP API: health checks, status, metrics, search endpoints

    private static final Logger logger = LoggerFactory.getLogger(HealthServer.class);

    private final HttpServer server; // |su:100 JDK built-in HTTP server - no external dependencies
    private final CrawlerEngine engine;
    private final int port;

    public HealthServer(CrawlerEngine engine, int port) throws IOException {
        this.engine = engine;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        // |su:101 REST endpoints - each path maps to a handler class
        server.createContext("/health", new HealthHandler()); // Simple health check
        server.createContext("/status", new StatusHandler()); // Detailed crawler status
        server.createContext("/metrics", new MetricsHandler()); // Raw JSON metrics
        server.createContext("/search", new SearchHandler()); // |su:102 Search: GET /search?q=java&limit=10

        // |su:103 Thread pool for HTTP requests - handles 4 concurrent API calls
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    /**
     * Start the HTTP server.
     */
    public void start() {
        server.start();
        logger.info("Health server started on port {}", port);
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        server.stop(5);
        logger.info("Health server stopped");
    }

    /**
     * GET /health - Simple health check
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String response = """
                {
                  "status": "healthy",
                  "service": "concurrent-web-crawler",
                  "version": "1.0.0"
                }
                """;
            sendJsonResponse(exchange, 200, response);
        }
    }

    /**
     * GET /status - Detailed crawler status
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            CrawlMetrics metrics = engine.getMetrics();
            String status = engine.isRunning() ? "running" :
                           (engine.isStopped() ? "stopped" : "idle");

            String response = String.format("""
                {
                  "status": "%s",
                  "pagesProcessed": %d,
                  "errors": %d,
                  "frontierSize": %d,
                  "uniqueDomains": %d,
                  "elapsedTime": "%s",
                  "pagesPerMinute": %.2f,
                  "bytesDownloaded": "%s"
                }
                """,
                status,
                metrics.getPagesProcessed(),
                metrics.getErrors(),
                engine.getFrontier().size(),
                metrics.getUniqueDomains(),
                metrics.getFormattedElapsedTime(),
                metrics.getPagesPerMinute(),
                metrics.getFormattedBytes()
            );

            sendJsonResponse(exchange, 200, response);
        }
    }

    /**
     * GET /metrics - Raw metrics as JSON
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String response = engine.getMetrics().toJson();
            sendJsonResponse(exchange, 200, response);
        }
    }

    /**
     * GET /search?q=query&limit=10 - Search indexed content
     */
    private class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            // Parse query parameters
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String query = params.get("q");
            int limit = 10;

            if (params.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(params.get("limit"));
                    limit = Math.min(Math.max(limit, 1), 100); // Clamp between 1 and 100
                } catch (NumberFormatException ignored) {
                }
            }

            if (query == null || query.isBlank()) {
                sendJsonResponse(exchange, 400, """
                    {"error": "Missing query parameter 'q'"}
                    """);
                return;
            }

            // Perform search
            ContentIndexer indexer = engine.getContentIndexer();
            List<ContentIndexer.SearchResult> results = indexer.search(query, limit);

            // Build response
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"query\": \"").append(escapeJson(query)).append("\",\n");
            json.append("  \"count\": ").append(results.size()).append(",\n");
            json.append("  \"results\": [\n");

            for (int i = 0; i < results.size(); i++) {
                ContentIndexer.SearchResult result = results.get(i);
                json.append("    {\"url\": \"").append(escapeJson(result.url()))
                    .append("\", \"score\": ").append(String.format("%.4f", result.score()))
                    .append("}");
                if (i < results.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("  ]\n}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        // CORS: In production, restrict to specific origins via ALLOWED_ORIGINS env var
        String allowedOrigins = System.getenv("ALLOWED_ORIGINS");
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "*"; // Default to allow all for development
        }
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigins);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (pair.length == 1) {
                params.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), "");
            }
        }

        return params;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
