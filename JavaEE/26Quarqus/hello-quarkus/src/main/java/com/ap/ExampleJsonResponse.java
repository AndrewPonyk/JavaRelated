package com.ap;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/hellojson")
@ApplicationScoped
public class ExampleJsonResponse {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HelloResponse getHello() {
        HelloResponse response = new HelloResponse();
        response.setMessage("Hello World!");
        response.setFrom("Quarkus");
        return response;
    }

    public static class HelloResponse {
        @JsonProperty("message")
        private String message;
        @JsonProperty("from")
        private String from;

        public HelloResponse(){

        }
        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }
}
