package org.computationalgeometry.model;

import java.util.Objects;

public final class Triangle2D {
    private final Point2D a;
    private final Point2D b;
    private final Point2D c;

    public Triangle2D(Point2D a, Point2D b, Point2D c) {
        Objects.requireNonNull(a, "triangle vertex a must not be null");
        Objects.requireNonNull(b, "triangle vertex b must not be null");
        Objects.requireNonNull(c, "triangle vertex c must not be null");
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public Point2D a() {
        return a;
    }

    public Point2D b() {
        return b;
    }

    public Point2D c() {
        return c;
    }

    @Override
    public String toString() {
        return a + " / " + b + " / " + c;
    }
}
