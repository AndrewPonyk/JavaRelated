package com.approval.workflow.verticles;

import com.approval.workflow.models.Document;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Reactive MongoDB persistence verticle handling all CRUD and aggregate queries.
 */
public class DatabaseVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(DatabaseVerticle.class);
    private static final String COLLECTION_DOCUMENTS = "documents";

    private MongoClient mongoClient;

    @Override
    public void start(Promise<Void> startPromise) {
        JsonObject mongoConfig = config().getJsonObject("mongodb", new JsonObject());
        mongoClient = MongoClient.createShared(vertx, mongoConfig);

        // Register EventBus Handlers
        vertx.eventBus().consumer("db.document.save", this::handleSave);
        vertx.eventBus().consumer("db.document.find", this::handleFind);
        vertx.eventBus().consumer("db.document.getById", this::handleGetById);
        vertx.eventBus().consumer("db.document.update", this::handleUpdate);

        log.info("DatabaseVerticle initialized with MongoDB reactive client.");
        startPromise.complete();
    }

    private void handleSave(Message<JsonObject> message) {
        JsonObject doc = message.body();
        if (!doc.containsKey("_id") && !doc.containsKey("id")) {
            String newId = "doc-" + UUID.randomUUID().toString().substring(0, 8);
            doc.put("_id", newId);
            doc.put("id", newId);
        } else if (!doc.containsKey("id") && doc.containsKey("_id")) {
            doc.put("id", doc.getString("_id"));
        } else if (!doc.containsKey("_id") && doc.containsKey("id")) {
            doc.put("_id", doc.getString("id"));
        }

        mongoClient.save(COLLECTION_DOCUMENTS, doc)
                .onSuccess(id -> {
                    log.debug("Document saved with ID: {}", id);
                    message.reply(sanitizeMongoJson(doc));
                })
                .onFailure(err -> {
                    log.error("Failed to save document to MongoDB: {}", err.getMessage());
                    message.fail(500, err.getMessage());
                });
    }

    private void handleFind(Message<JsonObject> message) {
        JsonObject filter = message.body();
        JsonObject mongoQuery = new JsonObject();

        if (filter.containsKey("status")) {
            mongoQuery.put("status", filter.getString("status"));
        }
        if (filter.containsKey("role")) {
            mongoQuery.put("approvalSteps.requiredRole", filter.getString("role"));
        }

        mongoClient.find(COLLECTION_DOCUMENTS, mongoQuery)
                .onSuccess(list -> {
                    JsonArray array = new JsonArray();
                    if (list != null) {
                        for (JsonObject doc : list) {
                            array.add(sanitizeMongoJson(doc));
                        }
                    }
                    message.reply(new JsonObject().put("documents", array).put("total", array.size()));
                })
                .onFailure(err -> {
                    log.error("Failed to query documents: {}", err.getMessage());
                    message.fail(500, err.getMessage());
                });
    }

    private void handleGetById(Message<JsonObject> message) {
        String id = message.body().getString("id");
        JsonObject query = buildIdQuery(id);

        mongoClient.findOne(COLLECTION_DOCUMENTS, query, null)
                .onSuccess(doc -> {
                    if (doc != null) {
                        message.reply(sanitizeMongoJson(doc));
                    } else {
                        message.reply(new JsonObject());
                    }
                })
                .onFailure(err -> message.fail(500, err.getMessage()));
    }

    private void handleUpdate(Message<JsonObject> message) {
        JsonObject payload = message.body();
        String id = payload.getString("id", payload.getString("_id"));
        JsonObject query = buildIdQuery(id);
        JsonObject update = new JsonObject().put("$set", payload);

        mongoClient.findOneAndUpdate(COLLECTION_DOCUMENTS, query, update)
                .onSuccess(updatedDoc -> message.reply(sanitizeMongoJson(payload)))
                .onFailure(err -> {
                    log.error("Failed to update document {}: {}", id, err.getMessage());
                    message.fail(500, err.getMessage());
                });
    }

    public static JsonObject buildIdQuery(String id) {
        if (id == null) return new JsonObject();
        String cleanId = id;
        if (cleanId.startsWith("{") && cleanId.contains("$oid")) {
            try {
                JsonObject parsed = new JsonObject(cleanId);
                if (parsed.containsKey("$oid")) {
                    cleanId = parsed.getString("$oid");
                }
            } catch (Exception ignored) {}
        }

        JsonArray orClauses = new JsonArray();
        orClauses.add(new JsonObject().put("_id", cleanId));
        orClauses.add(new JsonObject().put("id", cleanId));
        if (cleanId.length() == 24 && cleanId.matches("^[0-9a-fA-F]+$")) {
            orClauses.add(new JsonObject().put("_id", new JsonObject().put("$oid", cleanId)));
        }
        return new JsonObject().put("$or", orClauses);
    }

    public static JsonObject sanitizeMongoJson(JsonObject json) {
        if (json == null) return null;
        JsonObject result = new JsonObject();
        for (String key : json.fieldNames()) {
            result.put(key, sanitizeMongoValue(json.getValue(key)));
        }
        String id = Document.parseId(result);
        if (id != null) {
            result.put("id", id);
            result.put("_id", id);
        }
        return result;
    }

    private static Object sanitizeMongoValue(Object val) {
        if (val instanceof JsonObject) {
            JsonObject obj = (JsonObject) val;
            if (obj.containsKey("$oid")) {
                return obj.getString("$oid");
            }
            if (obj.containsKey("$date")) {
                return obj.getString("$date");
            }
            JsonObject clean = new JsonObject();
            for (String k : obj.fieldNames()) {
                clean.put(k, sanitizeMongoValue(obj.getValue(k)));
            }
            return clean;
        } else if (val instanceof JsonArray) {
            JsonArray arr = (JsonArray) val;
            JsonArray clean = new JsonArray();
            for (int i = 0; i < arr.size(); i++) {
                clean.add(sanitizeMongoValue(arr.getValue(i)));
            }
            return clean;
        }
        return val;
    }

    @Override
    public void stop() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
