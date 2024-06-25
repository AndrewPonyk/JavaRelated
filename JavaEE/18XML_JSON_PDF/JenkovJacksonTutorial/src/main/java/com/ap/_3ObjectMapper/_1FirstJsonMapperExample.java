package com.ap._3ObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class _1FirstJsonMapperExample {

    public static final String carJson =
            "{ \"brand\" : \"Mercedes\", \"doors\" : 5 }";

    public static void main(String[] args) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readValue(carJson, JsonNode.class);
        System.out.println(jsonNode.get("doors"));
        System.out.println(jsonNode.toString());
    }
}
