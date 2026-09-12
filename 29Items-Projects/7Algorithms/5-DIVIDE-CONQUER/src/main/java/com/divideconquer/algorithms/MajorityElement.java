package com.divideconquer.algorithms;

import java.util.OptionalInt;

public final class MajorityElement {
    private MajorityElement() {
    }

    public static OptionalInt find(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        int candidate = majorityCandidate(values, 0, values.length - 1);
        int count = 0;
        for (int value : values) {
            if (value == candidate) {
                count++;
            }
        }
        return count > values.length / 2 ? OptionalInt.of(candidate) : OptionalInt.empty();
    }

    private static int majorityCandidate(int[] values, int left, int right) {
        if (left == right) {
            return values[left];
        }
        int mid = left + (right - left) / 2;
        int leftCandidate = majorityCandidate(values, left, mid);
        int rightCandidate = majorityCandidate(values, mid + 1, right);
        if (leftCandidate == rightCandidate) {
            return leftCandidate;
        }
        int leftCount = countInRange(values, leftCandidate, left, right);
        int rightCount = countInRange(values, rightCandidate, left, right);
        return leftCount >= rightCount ? leftCandidate : rightCandidate;
    }

    private static int countInRange(int[] values, int target, int left, int right) {
        int count = 0;
        for (int i = left; i <= right; i++) {
            if (values[i] == target) {
                count++;
            }
        }
        return count;
    }
}
