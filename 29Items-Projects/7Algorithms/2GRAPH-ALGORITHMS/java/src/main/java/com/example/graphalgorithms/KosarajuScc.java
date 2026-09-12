package com.example.graphalgorithms;

import java.util.*;

public class KosarajuScc {
    public static List<List<String>> find(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        List<String> order = new ArrayList<>();
        for (String vertex : graph.keySet()) {
            if (!visited.contains(vertex)) {
                dfs(graph, vertex, visited, order);
            }
        }

        Map<String, List<String>> reversed = new HashMap<>();
        for (String vertex : graph.keySet()) {
            reversed.putIfAbsent(vertex, new ArrayList<>());
            for (String neighbor : graph.get(vertex)) {
                reversed.computeIfAbsent(neighbor, ignored -> new ArrayList<>()).add(vertex);
            }
        }

        visited.clear();
        List<List<String>> components = new ArrayList<>();
        Collections.reverse(order);
        for (String vertex : order) {
            if (!visited.contains(vertex)) {
                List<String> component = new ArrayList<>();
                reverseDfs(reversed, vertex, visited, component);
                components.add(component);
            }
        }
        return components;
    }

    private static void dfs(Map<String, List<String>> graph, String vertex, Set<String> visited, List<String> order) {
        visited.add(vertex);
        for (String neighbor : graph.getOrDefault(vertex, List.of())) {
            if (!visited.contains(neighbor)) {
                dfs(graph, neighbor, visited, order);
            }
        }
        order.add(vertex);
    }

    private static void reverseDfs(Map<String, List<String>> graph, String vertex, Set<String> visited, List<String> component) {
        visited.add(vertex);
        component.add(vertex);
        for (String neighbor : graph.getOrDefault(vertex, List.of())) {
            if (!visited.contains(neighbor)) {
                reverseDfs(graph, neighbor, visited, component);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Kosaraju strongly connected components");
        System.out.println(find(Map.of("A", List.of("B"), "B", List.of("C"), "C", List.of("A", "D"), "D", List.of())));
    }
}

