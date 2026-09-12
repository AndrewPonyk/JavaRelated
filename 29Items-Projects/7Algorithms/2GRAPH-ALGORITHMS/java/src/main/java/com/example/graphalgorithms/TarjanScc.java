package com.example.graphalgorithms;

import java.util.*;

public class TarjanScc {
    private int index = 0;
    private final Deque<String> stack = new ArrayDeque<>();
    private final Set<String> onStack = new HashSet<>();
    private final Map<String, Integer> indices = new HashMap<>();
    private final Map<String, Integer> lowlink = new HashMap<>();
    private final List<List<String>> components = new ArrayList<>();

    public static List<List<String>> find(Map<String, List<String>> graph) {
        TarjanScc solver = new TarjanScc();
        for (String vertex : graph.keySet()) {
            if (!solver.indices.containsKey(vertex)) {
                solver.strongConnect(graph, vertex);
            }
        }
        return solver.components;
    }

    private void strongConnect(Map<String, List<String>> graph, String vertex) {
        indices.put(vertex, index);
        lowlink.put(vertex, index);
        index++;
        stack.push(vertex);
        onStack.add(vertex);

        for (String neighbor : graph.getOrDefault(vertex, List.of())) {
            if (!indices.containsKey(neighbor)) {
                strongConnect(graph, neighbor);
                lowlink.put(vertex, Math.min(lowlink.get(vertex), lowlink.get(neighbor)));
            } else if (onStack.contains(neighbor)) {
                lowlink.put(vertex, Math.min(lowlink.get(vertex), indices.get(neighbor)));
            }
        }

        if (lowlink.get(vertex).equals(indices.get(vertex))) {
            List<String> component = new ArrayList<>();
            String node;
            do {
                node = stack.pop();
                onStack.remove(node);
                component.add(node);
            } while (!node.equals(vertex));
            components.add(component);
        }
    }

    public static void main(String[] args) {
        System.out.println("Tarjan strongly connected components");
        System.out.println(find(Map.of("A", List.of("B"), "B", List.of("C"), "C", List.of("A", "D"), "D", List.of())));
    }
}

