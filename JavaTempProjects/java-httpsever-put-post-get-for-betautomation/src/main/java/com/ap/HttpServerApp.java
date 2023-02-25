package com.ap;

import io.undertow.Undertow;
import io.undertow.io.Receiver;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpServerApp {
    private static final Integer SERVER_PORT = 9999;

    public static OptionsMethodHandler optionsMethodHandler = new OptionsMethodHandler();
    public static PutUpdateHandler putUpdateHandler = new PutUpdateHandler();
    public static GetMethodHandlder getMethodHandlder = new GetMethodHandlder();

    public static void main(String[] args) {
        new HttpServerApp().startJsonServer();
    }

    public  void startJsonServer(){
        System.out.println("Starting server on port: " + SERVER_PORT);
        Undertow server = buildServer();
        server.start();
    }

    private static Undertow buildServer() {
        return Undertow.builder()
                .addHttpListener(SERVER_PORT, "0.0.0.0")
                .setHandler(exchange -> {
                    if (exchange.getRequestMethod().equals(HttpString.tryFromString("OPTIONS"))) {
                        optionsMethodHandler.process(exchange);
                        return;
                    }
                    if (exchange.getRequestMethod().equals(HttpString.tryFromString("PUT"))) {
                        putUpdateHandler.process(exchange);
                        return;
                    }
                    if (exchange.getRequestMethod().equals(HttpString.tryFromString("GET"))) {
                        getMethodHandlder.process(exchange);
                        return;
                    }
                    //DEFAULT:  Sets the return Content-Type to text/html
                    exchange.getResponseHeaders()
                            .put(Headers.CONTENT_TYPE, "text/html");
                    exchange.getResponseSender()
                            .send("<html>" +
                                    "<body>" +
                                    "<h1>Hello, world!</h1>" +
                                    "</body>" +
                                    "</html>");
                }).build();
    }
}

 class InMemoryDataSource {
    private static
    Map<String, String> data = new HashMap<>();


    public static void put(String key, String object){
        data.put(key, object);
    }

    public static String get(String key){
        return data.get(key);
    }
}

 class GetMethodHandlder {
    public void process(HttpServerExchange exchange) throws IOException {
        final String relativePath = exchange.getRelativePath();
        final String data = InMemoryDataSource.get(relativePath);
        exchange.getResponseHeaders()
                .put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender()
                .send(data);
    }
}

 class PutUpdateHandler {
    public void process(HttpServerExchange exchange) throws IOException {
        final String relativePath = exchange.getRelativePath();

        exchange.getRequestReceiver().receiveFullString(new Receiver.FullStringCallback() {
            @Override
            public void handle(HttpServerExchange exchange, String message) {
                InMemoryDataSource.put(relativePath, message);
                exchange.getResponseHeaders()
                        .put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
                exchange.setStatusCode(200);
            }
        });
    }
}

 class OptionsMethodHandler {
    public void process(HttpServerExchange exchange){
        exchange.getResponseHeaders()
                .put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseHeaders()
                .put(Headers.CONTENT_TYPE, "text/html");
        exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
    }
}