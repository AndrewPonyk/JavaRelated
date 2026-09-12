package com.example.graphalgorithms;

import java.util.*;

public class Kruskal {
    public static class Edge {
        private final String from;
        private final String to;
        private final int weight;

        public Edge(String from, String to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        public String from() { return from; }
        public String to() { return to; }
        public int weight() { return weight; }
        public String toString() { return "(" + from + " -> " + to + ", " + weight + ")"; }
    }

    public static class Result {
        private final int totalWeight;
        private final List<Edge> edges;

        public Result(int totalWeight, List<Edge> edges) {
            this.totalWeight = totalWeight;
            this.edges = edges;
        }

        public int totalWeight() { return totalWeight; }
        public List<Edge> edges() { return edges; }
        public String toString() { return "Result{totalWeight=" + totalWeight + ", edges=" + edges + "}"; }
    }

    public static Result minimumSpanningTree(List<String> vertices, List<Edge> edges) {
        if (new HashSet<>(vertices).size() != vertices.size()) {
            throw new IllegalArgumentException("vertices must be unique");
        }
        Set<String> vertexSet = new HashSet<>(vertices);
        for (Edge edge : edges) {
            if (!vertexSet.contains(edge.from()) || !vertexSet.contains(edge.to())) {
                throw new IllegalArgumentException("edge references an unknown vertex");
            }
        }
        DisjointSet ds = new DisjointSet(vertices);
        List<Edge> tree = new ArrayList<>();
        int total = 0;
        edges.stream().sorted(Comparator.comparingInt(Edge::weight)).forEach(edge -> {
            if (ds.union(edge.from(), edge.to())) {
                tree.add(edge);
            }
        });
        for (Edge edge : tree) {
            total += edge.weight();
        }
        if (!vertices.isEmpty() && tree.size() != vertices.size() - 1) {
            throw new IllegalArgumentException("graph is disconnected");
        }
        return new Result(total, tree);
    }

    private static class DisjointSet {
        private final Map<String, String> parent = new HashMap<>();
        private final Map<String, Integer> rank = new HashMap<>();

        DisjointSet(List<String> vertices) {
            for (String vertex : vertices) {
                parent.put(vertex, vertex);
                rank.put(vertex, 0);
            }
        }

        String find(String vertex) {
            if (!parent.get(vertex).equals(vertex)) {
                parent.put(vertex, find(parent.get(vertex)));
            }
            return parent.get(vertex);
        }

        boolean union(String left, String right) {
            String rootLeft = find(left);
            String rootRight = find(right);
            if (rootLeft.equals(rootRight)) {
                return false;
            }
            if (rank.get(rootLeft) < rank.get(rootRight)) {
                parent.put(rootLeft, rootRight);
            } else if (rank.get(rootLeft) > rank.get(rootRight)) {
                parent.put(rootRight, rootLeft);
            } else {
                parent.put(rootRight, rootLeft);
                rank.put(rootLeft, rank.get(rootLeft) + 1);
            }
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("Kruskal minimum spanning tree");
        System.out.println(minimumSpanningTree(
            List.of("A", "B", "C", "D"),
            List.of(new Edge("A", "B", 1), new Edge("A", "C", 4), new Edge("B", "C", 2), new Edge("C", "D", 1))
        ));
    }
}
