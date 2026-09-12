package org.computationalgeometry.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.computationalgeometry.model.Point2D;

public final class InputLoader {
    private static final int DEFAULT_MAX_POINTS = 1_000_000;
    private static final Pattern JSON_POINT = Pattern.compile(
            "\\{\\s*\"x\"\\s*:\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*,\\s*\"y\"\\s*:\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*\\}");

    private InputLoader() {
    }

    public static List<Point2D> loadPoints(String fileName) throws IOException {
        return loadPoints(fileName, DEFAULT_MAX_POINTS);
    }

    public static List<Point2D> loadPoints(String fileName, int maxPoints) throws IOException {
        Path path = Path.of(fileName);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("input file does not exist: " + path);
        }
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".csv")) {
            return loadCsv(path, maxPoints);
        }
        if (lowerName.endsWith(".json")) {
            return loadJson(path, maxPoints);
        }
        throw new IllegalArgumentException("unsupported input format; expected .csv or .json");
    }

    private static List<Point2D> loadCsv(Path path, int maxPoints) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV input is empty");
        }
        String[] headers = lines.get(0).split(",");
        int xIndex = indexOf(headers, "x");
        int yIndex = indexOf(headers, "y");
        if (xIndex < 0 || yIndex < 0) {
            throw new IllegalArgumentException("CSV input must include x and y columns");
        }

        List<Point2D> points = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",");
            if (columns.length <= Math.max(xIndex, yIndex)) {
                throw new IllegalArgumentException("CSV row " + (i + 1) + " is missing x or y");
            }
            points.add(new Point2D(parseDouble(columns[xIndex], "CSV row " + (i + 1)),
                    parseDouble(columns[yIndex], "CSV row " + (i + 1))));
        }
        return validate(points, maxPoints);
    }

    private static List<Point2D> loadJson(Path path, int maxPoints) throws IOException {
        String content = Files.readString(path);
        Matcher matcher = JSON_POINT.matcher(content);
        List<Point2D> points = new ArrayList<>();
        while (matcher.find()) {
            points.add(new Point2D(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2))));
        }
        return validate(points, maxPoints);
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static double parseDouble(String value, String label) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException(label + " has non-numeric coordinates", exc);
        }
    }

    private static List<Point2D> validate(List<Point2D> points, int maxPoints) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("input must contain at least one point");
        }
        if (points.size() > maxPoints) {
            throw new IllegalArgumentException("input contains " + points.size() + " points; maximum is " + maxPoints);
        }
        return List.copyOf(points);
    }
}
