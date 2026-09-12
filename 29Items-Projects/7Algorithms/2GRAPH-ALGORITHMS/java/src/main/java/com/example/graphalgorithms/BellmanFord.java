package com.example.graphalgorithms;

import java.util.*;

public class BellmanFord {
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

    public static Map<String, Integer> shortestPaths(List<String> vertices, List<Edge> edges, String start) {
        if (!vertices.contains(start)) {
            throw new IllegalArgumentException("start vertex is not in the graph");
        }
        if (new HashSet<>(vertices).size() != vertices.size()) {
            throw new IllegalArgumentException("vertices must be unique");
        }
        Map<String, Integer> distance = new HashMap<>();
        for (String vertex : vertices) {
            distance.put(vertex, Integer.MAX_VALUE / 4);
        }
        distance.put(start, 0);

        for (int i = 0; i < vertices.size() - 1; i++) {
            boolean changed = false;
            for (Edge edge : edges) {
                if (!distance.containsKey(edge.from()) || !distance.containsKey(edge.to())) {
                    throw new IllegalArgumentException("edge references an unknown vertex");
                }
                int fromDistance = distance.get(edge.from());
                if (fromDistance < Integer.MAX_VALUE / 4 && fromDistance + edge.weight() < distance.get(edge.to())) {
                    distance.put(edge.to(), fromDistance + edge.weight());
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        for (Edge edge : edges) {
            if (!distance.containsKey(edge.from()) || !distance.containsKey(edge.to())) {
                throw new IllegalArgumentException("edge references an unknown vertex");
            }
            int fromDistance = distance.get(edge.from());
            if (fromDistance < Integer.MAX_VALUE / 4 && fromDistance + edge.weight() < distance.get(edge.to())) {
                throw new IllegalArgumentException("Graph contains a negative weight cycle");
            }
        }
        return distance;
    }

    public static void main(String[] args) {
        System.out.println("Bellman-Ford shortest paths from A");
        System.out.println(shortestPaths(
            List.of("A", "B", "C"),
            List.of(new Edge("A", "B", 4), new Edge("A", "C", 5), new Edge("B", "C", -2)),
            "A"
        ));
    }
}
