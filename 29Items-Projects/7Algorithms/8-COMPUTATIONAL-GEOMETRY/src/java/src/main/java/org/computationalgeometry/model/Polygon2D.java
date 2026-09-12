package org.computationalgeometry.model;

import java.util.List;
import java.util.Objects;

public final class Polygon2D {
    private final List<Point2D> vertices;

    public Polygon2D(List<Point2D> vertices) {
        Objects.requireNonNull(vertices, "polygon vertices must not be null");
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("polygon requires at least three vertices");
        }
        if (vertices.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("polygon vertices must not contain null");
        }
        this.vertices = List.copyOf(vertices);
    }

    public List<Point2D> vertices() {
        return vertices;
    }
}
