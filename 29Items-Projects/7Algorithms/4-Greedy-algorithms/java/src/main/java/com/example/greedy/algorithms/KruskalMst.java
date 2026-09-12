package com.example.greedy.algorithms;

import com.example.greedy.model.Edge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class KruskalMst {
    private KruskalMst() {
    }

    public static final class Result {
        private final List<Edge> edges;
        private final int totalWeight;

        public Result(List<Edge> edges, int totalWeight) {
            this.edges = List.copyOf(edges);
            this.totalWeight = totalWeight;
        }

        public int totalWeight() {
            return totalWeight;
        }

        @Override
        public String toString() {
            return "Result{edges=" + edges + ", totalWeight=" + totalWeight + "}";
        }
    }

    public static Result solve(int vertexCount, List<Edge> edges) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must be non-negative");
        }

        DisjointSet disjointSet = new DisjointSet(vertexCount);
        List<Edge> selected = new ArrayList<>();

        for (Edge edge : edges.stream()
                .sorted(Comparator.comparingInt(Edge::weight))
                .collect(Collectors.toList())) {
            if (edge.source() >= vertexCount || edge.target() >= vertexCount) {
                throw new IllegalArgumentException("edge endpoint is outside the graph");
            }
            if (disjointSet.union(edge.source(), edge.target())) {
                selected.add(edge);
                if (selected.size() == vertexCount - 1) {
                    break;
                }
            }
        }

        int totalWeight = selected.stream().mapToInt(Edge::weight).sum();
        return new Result(selected, totalWeight);
    }

    private static final class DisjointSet {
        private final int[] parent;
        private final int[] rank;

        private DisjointSet(int size) {
            parent = new int[size];
            rank = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private boolean union(int left, int right) {
            int rootLeft = find(left);
            int rootRight = find(right);
            if (rootLeft == rootRight) {
                return false;
            }
            if (rank[rootLeft] < rank[rootRight]) {
                int temp = rootLeft;
                rootLeft = rootRight;
                rootRight = temp;
            }
            parent[rootRight] = rootLeft;
            if (rank[rootLeft] == rank[rootRight]) {
                rank[rootLeft]++;
            }
            return true;
        }
    }
}
