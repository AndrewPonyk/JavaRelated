package com.example.graphalgorithms;

import java.util.*;

public class ArticulationPoints {
    private int time = 0;

    public static Set<String> find(Map<String, List<String>> graph) {
        ArticulationPoints solver = new ArticulationPoints();
        Set<String> visited = new HashSet<>();
        Map<String, Integer> discovery = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> points = new HashSet<>();
        for (String vertex : graph.keySet()) {
            if (!visited.contains(vertex)) {
                solver.dfs(graph, vertex, visited, discovery, low, parent, points);
            }
        }
        return points;
    }

    private void dfs(
        Map<String, List<String>> graph,
        String vertex,
        Set<String> visited,
        Map<String, Integer> discovery,
        Map<String, Integer> low,
        Map<String, String> parent,
        Set<String> points
    ) {
        visited.add(vertex);
        discovery.put(vertex, time);
        low.put(vertex, time);
        time++;
        int children = 0;
        for (String neighbor : graph.getOrDefault(vertex, List.of())) {
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, vertex);
                children++;
                dfs(graph, neighbor, visited, discovery, low, parent, points);
                low.put(vertex, Math.min(low.get(vertex), low.get(neighbor)));
                if (!parent.containsKey(vertex) && children > 1) {
                    points.add(vertex);
                }
                if (parent.containsKey(vertex) && low.get(neighbor) >= discovery.get(vertex)) {
                    points.add(vertex);
                }
            } else if (!neighbor.equals(parent.get(vertex))) {
                low.put(vertex, Math.min(low.get(vertex), discovery.get(neighbor)));
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Articulation points");
        System.out.println(find(Map.of("A", List.of("B"), "B", List.of("A", "C", "D"), "C", List.of("B"), "D", List.of("B"))));
    }
}

