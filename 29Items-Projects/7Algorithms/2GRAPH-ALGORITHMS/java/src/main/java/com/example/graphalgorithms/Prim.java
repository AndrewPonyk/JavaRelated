package com.example.graphalgorithms;

import java.util.*;

public class Prim {
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

    public static class TreeEdge {
        private final String from;
        private final String to;
        private final int weight;

        public TreeEdge(String from, String to, int weight) {
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
        private final List<TreeEdge> edges;

        public Result(int totalWeight, List<TreeEdge> edges) {
            this.totalWeight = totalWeight;
            this.edges = edges;
        }

        public int totalWeight() { return totalWeight; }
        public List<TreeEdge> edges() { return edges; }
        public String toString() { return "Result{totalWeight=" + totalWeight + ", edges=" + edges + "}"; }
    }

    private static class Candidate {
        private final String from;
        private final String to;
        private final int weight;

        Candidate(String from, String to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        String from() { return from; }
        String to() { return to; }
        int weight() { return weight; }
    }

    public static Result minimumSpanningTree(Map<String, List<Edge>> graph, String start) {
        if (!graph.containsKey(start)) {
            throw new IllegalArgumentException("start vertex is not in the graph");
        }
        Set<String> visited = new HashSet<>();
        List<TreeEdge> tree = new ArrayList<>();
        PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator.comparingInt(Candidate::weight));
        visited.add(start);
        for (Edge edge : graph.getOrDefault(start, List.of())) {
            queue.add(new Candidate(start, edge.to(), edge.weight()));
        }
        int total = 0;
        while (!queue.isEmpty() && visited.size() < graph.size()) {
            Candidate candidate = queue.poll();
            if (!visited.add(candidate.to())) {
                continue;
            }
            total += candidate.weight();
            tree.add(new TreeEdge(candidate.from(), candidate.to(), candidate.weight()));
            for (Edge edge : graph.getOrDefault(candidate.to(), List.of())) {
                if (!graph.containsKey(edge.to())) {
                    throw new IllegalArgumentException("edge references an unknown vertex: " + edge.to());
                }
                if (!visited.contains(edge.to())) {
                    queue.add(new Candidate(candidate.to(), edge.to(), edge.weight()));
                }
            }
        }
        if (visited.size() != graph.size()) {
            throw new IllegalArgumentException("graph is disconnected");
        }
        return new Result(total, tree);
    }

    public static void main(String[] args) {
        Map<String, List<Edge>> graph = Map.of(
            "A", List.of(new Edge("B", 1), new Edge("C", 4)),
            "B", List.of(new Edge("A", 1), new Edge("C", 2)),
            "C", List.of(new Edge("A", 4), new Edge("B", 2))
        );
        System.out.println("Prim minimum spanning tree");
        System.out.println(minimumSpanningTree(graph, "A"));
    }
}
