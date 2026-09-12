package com.divideconquer.algorithms;

public final class SegmentTree {
    private final int size;
    private final long[] tree;

    public SegmentTree(int[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        this.size = values.length;
        this.tree = new long[size * 4];
        build(values, 1, 0, size - 1);
    }

    public long rangeSum(int left, int right) {
        if (left < 0 || right >= size || left > right) {
            throw new IllegalArgumentException("invalid query range");
        }
        return rangeSum(1, 0, size - 1, left, right);
    }

    private void build(int[] values, int node, int left, int right) {
        if (left == right) {
            tree[node] = values[left];
            return;
        }
        int mid = left + (right - left) / 2;
        build(values, node * 2, left, mid);
        build(values, node * 2 + 1, mid + 1, right);
        tree[node] = tree[node * 2] + tree[node * 2 + 1];
    }

    private long rangeSum(int node, int left, int right, int queryLeft, int queryRight) {
        if (queryLeft <= left && right <= queryRight) {
            return tree[node];
        }
        int mid = left + (right - left) / 2;
        long sum = 0;
        if (queryLeft <= mid) {
            sum += rangeSum(node * 2, left, mid, queryLeft, queryRight);
        }
        if (queryRight > mid) {
            sum += rangeSum(node * 2 + 1, mid + 1, right, queryLeft, queryRight);
        }
        return sum;
    }
}
