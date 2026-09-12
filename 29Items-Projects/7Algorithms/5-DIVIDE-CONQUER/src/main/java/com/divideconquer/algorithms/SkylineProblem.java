package com.divideconquer.algorithms;

import java.util.ArrayList;
import java.util.List;

public final class SkylineProblem {
    private SkylineProblem() {
    }

    public static final class Building {
        private final int left;
        private final int right;
        private final int height;

        public Building(int left, int right, int height) {
            if (left >= right || height < 0) {
                throw new IllegalArgumentException("building must have left < right and non-negative height");
            }
            this.left = left;
            this.right = right;
            this.height = height;
        }

        public int left() {
            return left;
        }

        public int right() {
            return right;
        }

        public int height() {
            return height;
        }
    }

    public static final class KeyPoint {
        private final int x;
        private final int height;

        public KeyPoint(int x, int height) {
            this.x = x;
            this.height = height;
        }

        public int x() {
            return x;
        }

        public int height() {
            return height;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + height + ")";
        }
    }

    public static List<KeyPoint> skyline(List<Building> buildings) {
        if (buildings == null) {
            throw new IllegalArgumentException("buildings must not be null");
        }
        if (buildings.isEmpty()) {
            return List.of();
        }
        return solve(buildings, 0, buildings.size());
    }

    private static List<KeyPoint> solve(List<Building> buildings, int start, int end) {
        if (end - start == 1) {
            Building building = buildings.get(start);
            return List.of(new KeyPoint(building.left(), building.height()), new KeyPoint(building.right(), 0));
        }
        int mid = start + (end - start) / 2;
        return merge(solve(buildings, start, mid), solve(buildings, mid, end));
    }

    private static List<KeyPoint> merge(List<KeyPoint> left, List<KeyPoint> right) {
        List<KeyPoint> result = new ArrayList<>();
        int leftIndex = 0;
        int rightIndex = 0;
        int leftHeight = 0;
        int rightHeight = 0;

        while (leftIndex < left.size() && rightIndex < right.size()) {
            int x;
            if (left.get(leftIndex).x() < right.get(rightIndex).x()) {
                x = left.get(leftIndex).x();
                leftHeight = left.get(leftIndex++).height();
            } else if (right.get(rightIndex).x() < left.get(leftIndex).x()) {
                x = right.get(rightIndex).x();
                rightHeight = right.get(rightIndex++).height();
            } else {
                x = left.get(leftIndex).x();
                leftHeight = left.get(leftIndex++).height();
                rightHeight = right.get(rightIndex++).height();
            }
            append(result, x, Math.max(leftHeight, rightHeight));
        }
        while (leftIndex < left.size()) {
            KeyPoint point = left.get(leftIndex++);
            append(result, point.x(), point.height());
        }
        while (rightIndex < right.size()) {
            KeyPoint point = right.get(rightIndex++);
            append(result, point.x(), point.height());
        }
        return result;
    }

    private static void append(List<KeyPoint> result, int x, int height) {
        if (!result.isEmpty() && result.get(result.size() - 1).height() == height) {
            return;
        }
        if (!result.isEmpty() && result.get(result.size() - 1).x() == x) {
            result.set(result.size() - 1, new KeyPoint(x, height));
            return;
        }
        result.add(new KeyPoint(x, height));
    }
}
