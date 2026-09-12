package com.example.graphalgorithms;

import java.util.*;
import java.util.function.ToIntBiFunction;

public class AStar {
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

    public static class Result {
        private final int cost;
        private final List<String> path;

        public Result(int cost, List<String> path) {
            this.cost = cost;
            this.path = path;
        }

        public int cost() { return cost; }
        public List<String> path() { return path; }
        public String toString() { return "Result{cost=" + cost + ", path=" + path + "}"; }
    }

    private static class State {
        private final String vertex;
        private final int cost;
        private final int priority;

        State(String vertex, int cost, int priority) {
            this.vertex = vertex;
            this.cost = cost;
            this.priority = priority;
        }

        String vertex() { return vertex; }
        int cost() { return cost; }
        int priority() { return priority; }
    }

    public static Result search(
        Map<String, List<Edge>> graph,
        String start,
        String goal,
        ToIntBiFunction<String, String> heuristic
    ) {
        if (!graph.containsKey(start)) {
            throw new IllegalArgumentException("start vertex is not in the graph");
        }
        if (!graph.containsKey(goal)) {
            throw new IllegalArgumentException("goal vertex is not in the graph");
        }
        PriorityQueue<State> open = new PriorityQueue<>(Comparator.comparingInt(State::priority));
        Map<String, Integer> cost = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        for (String vertex : graph.keySet()) {
            cost.put(vertex, Integer.MAX_VALUE / 4);
        }
        cost.put(start, 0);
        open.add(new State(start, 0, heuristic.applyAsInt(start, goal)));

        while (!open.isEmpty()) {
            State current = open.poll();
            if (current.vertex().equals(goal)) {
                return new Result(current.cost(), reconstruct(previous, goal));
            }
            for (Edge edge : graph.getOrDefault(current.vertex(), List.of())) {
                if (!graph.containsKey(edge.to())) {
                    throw new IllegalArgumentException("edge references an unknown vertex: " + edge.to());
                }
                if (edge.weight() < 0) {
                    throw new IllegalArgumentException("A* does not support negative edge weights");
                }
                int candidate = current.cost() + edge.weight();
                if (candidate < cost.getOrDefault(edge.to(), Integer.MAX_VALUE / 4)) {
                    previous.put(edge.to(), current.vertex());
                    cost.put(edge.to(), candidate);
                    open.add(new State(edge.to(), candidate, candidate + heuristic.applyAsInt(edge.to(), goal)));
                }
            }
        }
        return new Result(Integer.MAX_VALUE / 4, List.of());
    }

    private static List<String> reconstruct(Map<String, String> previous, String goal) {
        LinkedList<String> path = new LinkedList<>();
        String current = goal;
        path.addFirst(current);
        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.addFirst(current);
        }
        return path;
    }

    public static void main(String[] args) {
        Map<String, List<Edge>> graph = Map.of(
            "A", List.of(new Edge("B", 1), new Edge("C", 4)),
            "B", List.of(new Edge("D", 2)),
            "C", List.of(new Edge("D", 1)),
            "D", List.of()
        );
        System.out.println("A* path from A to D");
        System.out.println(search(graph, "A", "D", (node, goal) -> 0));
    }
}
