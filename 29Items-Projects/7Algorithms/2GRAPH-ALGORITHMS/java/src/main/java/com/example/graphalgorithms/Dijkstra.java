package com.example.graphalgorithms;

import java.util.*;

public class Dijkstra {
    public static class Edge {
        private final String to;
        private final int weight;

        public Edge(String to, int weight) {
            this.to = to;
            this.weight = weight;
        }

        public String to() { return to; }
        public int weight() { return weight; }
        public String toString() { return "(" + to + ", " + weight + ")"; }
    }

    private static class State {
        private final String vertex;
        private final int distance;

        State(String vertex, int distance) {
            this.vertex = vertex;
            this.distance = distance;
        }

        String vertex() { return vertex; }
        int distance() { return distance; }
    }

    public static Map<String, Integer> shortestPaths(Map<String, List<Edge>> graph, String start) {
        if (!graph.containsKey(start)) {
            throw new IllegalArgumentException("start vertex is not in the graph");
        }
        Map<String, Integer> distance = new HashMap<>();
        for (String vertex : graph.keySet()) {
            distance.put(vertex, Integer.MAX_VALUE / 4);
        }
        distance.put(start, 0);

        PriorityQueue<State> queue = new PriorityQueue<>(Comparator.comparingInt(State::distance));
        queue.add(new State(start, 0));
        while (!queue.isEmpty()) {
            State current = queue.poll();
            if (current.distance() > distance.get(current.vertex())) {
                continue;
            }
            for (Edge edge : graph.getOrDefault(current.vertex(), List.of())) {
                if (!graph.containsKey(edge.to())) {
                    throw new IllegalArgumentException("edge references an unknown vertex: " + edge.to());
                }
                if (edge.weight() < 0) {
                    throw new IllegalArgumentException("Dijkstra does not support negative edge weights");
                }
                int candidate = current.distance() + edge.weight();
                if (candidate < distance.getOrDefault(edge.to(), Integer.MAX_VALUE / 4)) {
                    distance.put(edge.to(), candidate);
                    queue.add(new State(edge.to(), candidate));
                }
            }
        }
        return distance;
    }

    public static void main(String[] args) {
        Map<String, List<Edge>> graph = Map.of(
            "A", List.of(new Edge("B", 4), new Edge("C", 2)),
            "B", List.of(new Edge("D", 1)),
            "C", List.of(new Edge("B", 1), new Edge("D", 5)),
            "D", List.of()
        );
        System.out.println("Dijkstra shortest paths from A");
        System.out.println(shortestPaths(graph, "A"));
    }
}
