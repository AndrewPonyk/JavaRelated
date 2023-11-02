package com.ap.hello_vertx_crud_mongo.controller;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class BookController {
  private final MongoClient mongoClient;

  public BookController(MongoClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  public void getAllBooks(RoutingContext routingContext) {
    mongoClient.find("books", new JsonObject(), result -> {
      if (result.succeeded()) {
        routingContext.response()
          .putHeader("content-type", "application/json; charset=utf-8")
          .end(Json.encode(result.result()));
      } else {
        routingContext.response().setStatusCode(404).end();
      }
    });
  }

  public void getBookById(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    JsonObject query = new JsonObject().put("_id", id);
    mongoClient.findOne("books", query, null, result -> {
      if (result.succeeded()) {
        if (result.result() == null) {
          routingContext.response().setStatusCode(404).end();
        } else {
          routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encode(result.result()));
        }
      } else {
        routingContext.response().setStatusCode(404).end();
      }
    });
  }

  public void createBook(RoutingContext routingContext) {
    String bodyAsString = routingContext.getBodyAsString();
    Book book = Json.decodeValue(bodyAsString, Book.class);
    JsonObject document = JsonObject.mapFrom(book);
    mongoClient.insert("books", document, result -> {
      if (result.succeeded()) {
        routingContext.response()
          .putHeader("content-type", "application/json; charset=utf-8")
          .end(Json.encode(document));
      } else {
        routingContext.response().setStatusCode(404).end();
      }
    });
  }

  public void updateBook(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    JsonObject query = new JsonObject().put("_id", id);
    mongoClient.findOneAndReplace("books", query, routingContext.getBodyAsJson(), result -> {
      if (result.succeeded()) {
        if (result.result() == null) {
          routingContext.response().setStatusCode(404).end();
        } else {
          routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encode(result.result()));
        }
      } else {
        routingContext.response().setStatusCode(404).end();
      }
    });
  }

  public void deleteBook(RoutingContext routingContext) {
    String id = routingContext.request().getParam("id");
    JsonObject query = new JsonObject().put("_id", id);
    mongoClient.findOneAndDelete("books", query, result -> {
      if (result.succeeded()) {
        if (result.result() == null) {
          routingContext.response().setStatusCode(404).end();
        } else {
          routingContext.response().setStatusCode(204).end();
        }
      } else {
        routingContext.response().setStatusCode(404).end();
      }
    });
  }

}
