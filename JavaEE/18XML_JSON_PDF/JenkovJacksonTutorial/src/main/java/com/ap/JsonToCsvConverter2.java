package com.ap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.regex.*;


public class JsonToCsvConverter2 {
    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Paths.get("src/main/resources/Toka-stations.json").toFile());
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("output-old.csv"))) {
            writer.write("lat;lng;name;portsCount;portsComment\n");
            for (JsonNode node : root) {
                double lat = node.path("lat").asDouble();
                double lng = node.path("lng").asDouble();
                String name = node.path("name").asText();
                String portsCount = node.path("portsCount").asText();
                String portsComment = node.path("portsComment").asText();
                writer.write(lat + ";" + lng + ";" + name + ";" + portsCount + ";" + portsComment + "\n");
            }
        }
    }
}