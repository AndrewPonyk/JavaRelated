package org.computationalgeometry.algorithms;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import org.computationalgeometry.model.Point2D;

public final class ClosestPair {
    private ClosestPair() {
    }

    public static final class Result {
        private final Point2D first;
        private final Point2D second;
        private final double distance;

        public Result(Point2D first, Point2D second, double distance) {
            this.first = first;
            this.second = second;
            this.distance = distance;
        }

        public Point2D first() {
            return first;
        }

        public Point2D second() {
            return second;
        }

        public double distance() {
            return distance;
        }
    }

    public static Result find(List<Point2D> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("closest pair requires at least two points");
        }

        HashSet<Point2D> seen = new HashSet<>();
        for (Point2D point : points) {
            if (!seen.add(point)) {
                return new Result(point, point, 0.0);
            }
        }

        List<Point2D> byX = new ArrayList<>(points);
        byX.sort(Point2D::compareTo);
        List<Point2D> byY = new ArrayList<>(points);
        byY.sort(Comparator.comparingDouble(Point2D::y).thenComparingDouble(Point2D::x));
        PartialResult partial = closestRecursive(byX, byY);
        return new Result(partial.first, partial.second, Math.sqrt(partial.distanceSquared));
    }

    private static PartialResult closestRecursive(List<Point2D> byX, List<Point2D> byY) {
        if (byX.size() <= 3) {
            return bruteForce(byX);
        }

        int midpoint = byX.size() / 2;
        List<Point2D> leftX = new ArrayList<>(byX.subList(0, midpoint));
        List<Point2D> rightX = new ArrayList<>(byX.subList(midpoint, byX.size()));
        double splitX = byX.get(midpoint).x();
        HashSet<Point2D> leftSet = new HashSet<>(leftX);
        List<Point2D> leftY = new ArrayList<>();
        List<Point2D> rightY = new ArrayList<>();
        for (Point2D point : byY) {
            if (leftSet.contains(point)) {
                leftY.add(point);
            } else {
                rightY.add(point);
            }
        }

        PartialResult left = closestRecursive(leftX, leftY);
        PartialResult right = closestRecursive(rightX, rightY);
        PartialResult best = left.distanceSquared <= right.distanceSquared ? left : right;

        List<Point2D> strip = new ArrayList<>();
        for (Point2D point : byY) {
            double dx = point.x() - splitX;
            if (dx * dx < best.distanceSquared) {
                strip.add(point);
            }
        }

        for (int i = 0; i < strip.size(); i++) {
            for (int j = i + 1; j < strip.size() && j < i + 8; j++) {
                double candidate = strip.get(i).distanceSquared(strip.get(j));
                if (candidate < best.distanceSquared) {
                    best = new PartialResult(strip.get(i), strip.get(j), candidate);
                }
            }
        }

        return best;
    }

    private static PartialResult bruteForce(List<Point2D> points) {
        Point2D bestA = points.get(0);
        Point2D bestB = points.get(1);
        double bestDistanceSquared = bestA.distanceSquared(bestB);
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double candidate = points.get(i).distanceSquared(points.get(j));
                if (candidate < bestDistanceSquared) {
                    bestA = points.get(i);
                    bestB = points.get(j);
                    bestDistanceSquared = candidate;
                }
            }
        }
        return new PartialResult(bestA, bestB, bestDistanceSquared);
    }

    private static final class PartialResult {
        private final Point2D first;
        private final Point2D second;
        private final double distanceSquared;

        private PartialResult(Point2D first, Point2D second, double distanceSquared) {
            this.first = first;
            this.second = second;
            this.distanceSquared = distanceSquared;
        }
    }
}
