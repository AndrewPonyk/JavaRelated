package com.ap._4JsonParser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;

public class JsonParserExample {

    public static String carJson =
            "{ \"brand\" : \"Mercedes\", \"doors\" : 5 }";

    public static void main(String[] args) throws IOException {
        /*The Jackson JsonParser class is a low level JSON parser. It is similar to the Java StAX parser for XML,
        except the JsonParser parses JSON and not XML.

        The Jackson JsonParser works at a lower level than the Jackson ObjectMapper.
        This makes the JsonParser faster than the ObjectMapper, but also more cumbersome to work with.
        * */

        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(carJson);

        while (!parser.isClosed()) {
            JsonToken jsonToken = parser.nextToken();
            System.out.println(jsonToken);

            // getting values
            if (JsonToken.FIELD_NAME.equals(jsonToken)) {
                jsonToken = parser.nextToken();
                System.out.println(parser.getValueAsString());
            }
        }
    }
}