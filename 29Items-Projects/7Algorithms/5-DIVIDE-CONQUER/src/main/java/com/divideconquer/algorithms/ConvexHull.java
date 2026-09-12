package com.divideconquer.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ConvexHull {
    private ConvexHull() {
    }

    public static final class Point {
        private final double x;
        private final double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Point)) {
                return false;
            }
            Point point = (Point) other;
            return Double.compare(point.x, x) == 0 && Double.compare(point.y, y) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }

    public static List<Point> monotonicChain(List<Point> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (points.stream().anyMatch(point -> point == null)) {
            throw new IllegalArgumentException("points must not contain null values");
        }

        List<Point> sorted = new ArrayList<>();
        for (Point point : points) {
            if (!sorted.contains(point)) {
                sorted.add(point);
            }
        }
        sorted.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        if (sorted.size() <= 1) {
            return sorted;
        }

        List<Point> lower = new ArrayList<>();
        for (Point point : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<Point> upper = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Point point = sorted.get(i);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static double cross(Point origin, Point a, Point b) {
        return (a.x() - origin.x()) * (b.y() - origin.y()) - (a.y() - origin.y()) * (b.x() - origin.x());
    }
}
