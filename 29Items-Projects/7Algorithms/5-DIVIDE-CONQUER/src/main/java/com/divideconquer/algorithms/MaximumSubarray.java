package com.divideconquer.algorithms;

public final class MaximumSubarray {
    private MaximumSubarray() {
    }

    public static final class Result {
        private final int startInclusive;
        private final int endInclusive;
        private final int sum;

        public Result(int startInclusive, int endInclusive, int sum) {
            this.startInclusive = startInclusive;
            this.endInclusive = endInclusive;
            this.sum = sum;
        }

        public int startInclusive() {
            return startInclusive;
        }

        public int endInclusive() {
            return endInclusive;
        }

        public int sum() {
            return sum;
        }

        @Override
        public String toString() {
            return "Result{start=" + startInclusive + ", end=" + endInclusive + ", sum=" + sum + "}";
        }
    }

    public static Result find(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return find(values, 0, values.length - 1);
    }

    private static Result find(int[] values, int left, int right) {
        if (left == right) {
            return new Result(left, right, values[left]);
        }

        int mid = left + (right - left) / 2;
        Result leftBest = find(values, left, mid);
        Result rightBest = find(values, mid + 1, right);
        Result crossingBest = crossing(values, left, mid, right);
        return maxBySum(leftBest, rightBest, crossingBest);
    }

    private static Result crossing(int[] values, int left, int mid, int right) {
        int bestLeftSum = Integer.MIN_VALUE;
        int sum = 0;
        int bestLeft = mid;
        for (int index = mid; index >= left; index--) {
            sum += values[index];
            if (sum > bestLeftSum) {
                bestLeftSum = sum;
                bestLeft = index;
            }
        }

        int bestRightSum = Integer.MIN_VALUE;
        sum = 0;
        int bestRight = mid + 1;
        for (int index = mid + 1; index <= right; index++) {
            sum += values[index];
            if (sum > bestRightSum) {
                bestRightSum = sum;
                bestRight = index;
            }
        }

        return new Result(bestLeft, bestRight, bestLeftSum + bestRightSum);
    }

    private static Result maxBySum(Result first, Result second, Result third) {
        Result best = first.sum() >= second.sum() ? first : second;
        return best.sum() >= third.sum() ? best : third;
    }
}
