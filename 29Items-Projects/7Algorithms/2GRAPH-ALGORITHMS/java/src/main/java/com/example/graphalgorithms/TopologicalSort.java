package com.example.graphalgorithms;

import java.util.*;

public class TopologicalSort {
    public static List<String> sort(Map<String, List<String>> graph) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (String vertex : graph.keySet()) {
            indegree.putIfAbsent(vertex, 0);
            for (String neighbor : graph.get(vertex)) {
                indegree.put(neighbor, indegree.getOrDefault(neighbor, 0) + 1);
            }
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String vertex = queue.remove();
            order.add(vertex);
            for (String neighbor : graph.getOrDefault(vertex, List.of())) {
                indegree.put(neighbor, indegree.get(neighbor) - 1);
                if (indegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }
        if (order.size() != indegree.size()) {
            throw new IllegalArgumentException("Graph contains a cycle");
        }
        return order;
    }

    public static void main(String[] args) {
        System.out.println("Topological sort");
        System.out.println(sort(Map.of("plan", List.of("code"), "code", List.of("test"), "test", List.of())));
    }
}

