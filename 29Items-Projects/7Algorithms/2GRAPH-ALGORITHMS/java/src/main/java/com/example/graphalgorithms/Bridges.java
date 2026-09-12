package com.example.graphalgorithms;

import java.util.*;

public class Bridges {
    public static class Bridge {
        private final String from;
        private final String to;

        public Bridge(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String from() { return from; }
        public String to() { return to; }
        public String toString() { return "(" + from + " - " + to + ")"; }
    }
    private int time = 0;

    public static List<Bridge> find(Map<String, List<String>> graph) {
        Bridges solver = new Bridges();
        Set<String> visited = new HashSet<>();
        Map<String, Integer> discovery = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        List<Bridge> bridges = new ArrayList<>();
        for (String vertex : graph.keySet()) {
            if (!visited.contains(vertex)) {
                solver.dfs(graph, vertex, visited, discovery, low, parent, bridges);
            }
        }
        return bridges;
    }

    private void dfs(
        Map<String, List<String>> graph,
        String vertex,
        Set<String> visited,
        Map<String, Integer> discovery,
        Map<String, Integer> low,
        Map<String, String> parent,
        List<Bridge> bridges
    ) {
        visited.add(vertex);
        discovery.put(vertex, time);
        low.put(vertex, time);
        time++;
        for (String neighbor : graph.getOrDefault(vertex, List.of())) {
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, vertex);
                dfs(graph, neighbor, visited, discovery, low, parent, bridges);
                low.put(vertex, Math.min(low.get(vertex), low.get(neighbor)));
                if (low.get(neighbor) > discovery.get(vertex)) {
                    bridges.add(new Bridge(vertex, neighbor));
                }
            } else if (!neighbor.equals(parent.get(vertex))) {
                low.put(vertex, Math.min(low.get(vertex), discovery.get(neighbor)));
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Bridges");
        System.out.println(find(Map.of("A", List.of("B"), "B", List.of("A", "C", "D"), "C", List.of("B"), "D", List.of("B"))));
    }
}
