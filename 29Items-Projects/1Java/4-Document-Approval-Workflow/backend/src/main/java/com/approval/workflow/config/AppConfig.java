package com.approval.workflow.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;

/**
 * Loads configuration from JSON files, system properties, and environment variables.
 */
public class AppConfig {

    public static Future<JsonObject> load(Vertx vertx) {
        // 1. File Store (application.json)
        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setConfig(new JsonObject().put("path", "application.json"));

        // 2. Environment Variables Store
        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env");

        // 3. System Properties Store
        ConfigStoreOptions sysStore = new ConfigStoreOptions()
                .setType("sys");

        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(fileStore)
                .addStore(sysStore)
                .addStore(envStore);

        ConfigRetriever retriever = ConfigRetriever.create(vertx, options);
        return retriever.getConfig().map(AppConfig::normalizeConfig);
    }

    public static JsonObject normalizeConfig(JsonObject config) {
        JsonObject result = config != null ? config.copy() : new JsonObject();

        // 1. Merge MongoDB config
        JsonObject mongo = result.getJsonObject("mongodb", new JsonObject()).copy();
        if (result.containsKey("MONGODB_URI") && result.getString("MONGODB_URI") != null && !result.getString("MONGODB_URI").isBlank()) {
            mongo.put("connection_string", result.getString("MONGODB_URI"));
        }
        if (result.containsKey("MONGODB_DB_NAME") && result.getString("MONGODB_DB_NAME") != null && !result.getString("MONGODB_DB_NAME").isBlank()) {
            mongo.put("db_name", result.getString("MONGODB_DB_NAME"));
        }
        result.put("mongodb", mongo);

        // 2. Merge NLP microservice config
        JsonObject nlp = result.getJsonObject("nlp", new JsonObject()).copy();
        if (result.containsKey("NLP_SERVICE_HOST") && result.getString("NLP_SERVICE_HOST") != null && !result.getString("NLP_SERVICE_HOST").isBlank()) {
            nlp.put("host", result.getString("NLP_SERVICE_HOST"));
        }
        if (result.containsKey("NLP_SERVICE_PORT") && result.getValue("NLP_SERVICE_PORT") != null) {
            try {
                nlp.put("port", Integer.parseInt(result.getValue("NLP_SERVICE_PORT").toString()));
            } catch (Exception ignored) {}
        }
        result.put("nlp", nlp);

        // 3. Merge HTTP config
        JsonObject http = result.getJsonObject("http", new JsonObject()).copy();
        if (result.containsKey("HTTP_PORT") && result.getValue("HTTP_PORT") != null) {
            try {
                http.put("port", Integer.parseInt(result.getValue("HTTP_PORT").toString()));
            } catch (Exception ignored) {}
        }
        if (result.containsKey("HTTP_HOST") && result.getString("HTTP_HOST") != null && !result.getString("HTTP_HOST").isBlank()) {
            http.put("host", result.getString("HTTP_HOST"));
        }
        result.put("http", http);

        // 4. Merge JWT config
        JsonObject jwt = result.getJsonObject("jwt", new JsonObject()).copy();
        if (result.containsKey("JWT_SECRET") && result.getString("JWT_SECRET") != null && !result.getString("JWT_SECRET").isBlank()) {
            jwt.put("secret", result.getString("JWT_SECRET"));
        }
        result.put("jwt", jwt);

        return result;
    }
}
