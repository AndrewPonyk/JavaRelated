package org.computationalgeometry.model;

import java.util.Objects;

public final class Point2D implements Comparable<Point2D> {
    private final double x;
    private final double y;

    public Point2D(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("point coordinates must be finite numbers");
        }
        this.x = x;
        this.y = y;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double distanceSquared(Point2D other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return dx * dx + dy * dy;
    }

    @Override
    public int compareTo(Point2D other) {
        int byX = Double.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        return Double.compare(y, other.y);
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f)", x, y);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Point2D)) {
            return false;
        }
        Point2D point = (Point2D) other;
        return Double.compare(point.x, x) == 0 && Double.compare(point.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
