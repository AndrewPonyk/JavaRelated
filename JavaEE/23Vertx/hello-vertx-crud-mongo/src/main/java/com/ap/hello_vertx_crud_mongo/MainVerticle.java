package com.ap.hello_vertx_crud_mongo;

import com.ap.hello_vertx_crud_mongo.controller.BookController;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends AbstractVerticle {


  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  private MongoClient mongoClient;
  private BookController bookController;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(
      new ConfigStoreOptions()
        .setType("file")
        .setConfig(new JsonObject().put("path", getClass().getResource("/config.json").getPath()))
        .setFormat("json")
    );
    ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

    retriever.getConfig(config -> {
      if (config.failed()) {
        startPromise.fail(config.cause());
        return;
      }

      String mongoURI = config.result().getString("mongo_uri");

      MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject().put("connection_string", mongoURI));

      Router router = Router.router(vertx);
      router.route().handler(BodyHandler.create());
      BookController bookController = new BookController(mongoClient);

      router.get("/books").handler(bookController::getAllBooks);
      router.get("/books/:id").handler(bookController::getBookById);
      router.post("/books").handler(bookController::createBook);
      router.put("/books/:id").handler(bookController::updateBook);
      router.delete("/books/:id").handler(bookController::deleteBook);

      vertx.createHttpServer()
        .requestHandler(router)
        .listen(8888, http -> {
          if (http.succeeded()) {
            startPromise.complete();
            System.out.println("HTTP server started on port 8080");
          } else {
            startPromise.fail(http.cause());
          }
        });
    });
  }

  @Override
  public void stop() throws Exception {
    mongoClient.close();
  }
}
