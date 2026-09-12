package com.example.graphalgorithms;

import java.util.*;

public class FloydWarshall {
    public static final int INF = 1_000_000_000;
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

    public static Map<String, Map<String, Integer>> allPairs(List<String> vertices, List<Edge> edges) {
        if (new HashSet<>(vertices).size() != vertices.size()) {
            throw new IllegalArgumentException("vertices must be unique");
        }
        Map<String, Map<String, Integer>> distance = new LinkedHashMap<>();
        for (String from : vertices) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String to : vertices) {
                row.put(to, from.equals(to) ? 0 : INF);
            }
            distance.put(from, row);
        }
        for (Edge edge : edges) {
            if (!distance.containsKey(edge.from()) || !distance.containsKey(edge.to())) {
                throw new IllegalArgumentException("edge references an unknown vertex");
            }
            distance.get(edge.from()).put(edge.to(), Math.min(distance.get(edge.from()).get(edge.to()), edge.weight()));
        }
        for (String middle : vertices) {
            for (String from : vertices) {
                for (String to : vertices) {
                    int candidate = distance.get(from).get(middle) + distance.get(middle).get(to);
                    if (candidate < distance.get(from).get(to)) {
                        distance.get(from).put(to, candidate);
                    }
                }
            }
        }
        return distance;
    }

    public static void main(String[] args) {
        System.out.println("Floyd-Warshall all-pairs shortest paths");
        System.out.println(allPairs(
            List.of("A", "B", "C"),
            List.of(new Edge("A", "B", 3), new Edge("B", "C", 1), new Edge("A", "C", 10))
        ));
    }
}
