package com.divideconquer.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ClosestPair {
    private ClosestPair() {
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

    public static final class Result {
        private final Point first;
        private final Point second;
        private final double distance;

        public Result(Point first, Point second, double distance) {
            this.first = first;
            this.second = second;
            this.distance = distance;
        }

        public Point first() {
            return first;
        }

        public Point second() {
            return second;
        }

        public double distance() {
            return distance;
        }
    }

    public static Result find(List<Point> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("at least two points are required");
        }
        if (points.stream().anyMatch(point -> point == null)) {
            throw new IllegalArgumentException("points must not contain null values");
        }
        List<Point> byX = new ArrayList<>(points);
        byX.sort(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::y));
        List<Point> byY = new ArrayList<>(points);
        byY.sort(Comparator.comparingDouble(Point::y).thenComparingDouble(Point::x));
        return closest(byX, byY);
    }

    private static Result closest(List<Point> byX, List<Point> byY) {
        int n = byX.size();
        if (n <= 3) {
            return bruteForce(byX);
        }

        int mid = n / 2;
        Point midPoint = byX.get(mid);
        List<Point> leftX = new ArrayList<>(byX.subList(0, mid));
        List<Point> rightX = new ArrayList<>(byX.subList(mid, n));
        List<Point> leftY = new ArrayList<>();
        List<Point> rightY = new ArrayList<>();

        for (Point point : byY) {
            if (point.x() < midPoint.x() || (point.x() == midPoint.x() && leftY.size() < leftX.size())) {
                leftY.add(point);
            } else {
                rightY.add(point);
            }
        }

        Result left = closest(leftX, leftY);
        Result right = closest(rightX, rightY);
        Result best = left.distance() <= right.distance() ? left : right;

        List<Point> strip = new ArrayList<>();
        for (Point point : byY) {
            if (Math.abs(point.x() - midPoint.x()) < best.distance()) {
                strip.add(point);
            }
        }

        for (int i = 0; i < strip.size(); i++) {
            for (int j = i + 1; j < strip.size() && strip.get(j).y() - strip.get(i).y() < best.distance(); j++) {
                Result candidate = result(strip.get(i), strip.get(j));
                if (candidate.distance() < best.distance()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static Result bruteForce(List<Point> points) {
        Result best = result(points.get(0), points.get(1));
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                Result candidate = result(points.get(i), points.get(j));
                if (candidate.distance() < best.distance()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static Result result(Point a, Point b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return new Result(a, b, Math.hypot(dx, dy));
    }
}
