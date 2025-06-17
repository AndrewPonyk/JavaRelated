package com.ap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonToCsvConverter {
    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Paths.get("src/main/resources/електрозарядки.json").toFile());
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("output.csv"))) {
            for (JsonNode node : root) {
                double lat = node.path("position").path("lat").asDouble();
                double lng = node.path("position").path("lng").asDouble();
                String content = node.path("content").asText();

                // Extract name and address from HTML content
                Pattern p = Pattern.compile("<strong>([^\\[]+)(?:\\[.*?\\])?</strong>.*?\\((.*?)\\)", Pattern.DOTALL);
                Matcher m = p.matcher(content);
                // Extract name
                String name = "";
                Pattern namePattern = Pattern.compile("<strong>([^<]+)</strong>");
                Matcher nameMatcher = namePattern.matcher(content);
                if (nameMatcher.find()) {
                    name = nameMatcher.group(1).trim();
                }

// Extract address
                String address = "";
                Pattern addressPattern = Pattern.compile("\\(([^)]+)\\)");
                Matcher addressMatcher = addressPattern.matcher(content);
                if (addressMatcher.find()) {
                    address = addressMatcher.group(1).trim();
                }

                String ports = node.path("ports").toString();
                writer.write(String.format("%s; %s; %s (%s); %s%n", lat, lng, name, address, ports));
            }
        }
    }
}