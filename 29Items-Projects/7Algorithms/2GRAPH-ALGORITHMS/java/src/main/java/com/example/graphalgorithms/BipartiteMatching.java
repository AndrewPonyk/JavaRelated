package com.example.graphalgorithms;

import java.util.*;

public class BipartiteMatching {
    public static Map<String, String> maximumMatching(Map<String, List<String>> graph) {
        Map<String, String> matchRight = new HashMap<>();
        for (String left : graph.keySet()) {
            tryMatch(left, graph, new HashSet<>(), matchRight);
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : matchRight.entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        return result;
    }

    private static boolean tryMatch(
        String left,
        Map<String, List<String>> graph,
        Set<String> seen,
        Map<String, String> matchRight
    ) {
        for (String right : graph.getOrDefault(left, List.of())) {
            if (!seen.add(right)) {
                continue;
            }
            if (!matchRight.containsKey(right) || tryMatch(matchRight.get(right), graph, seen, matchRight)) {
                matchRight.put(right, left);
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println("Bipartite matching");
        System.out.println(maximumMatching(Map.of("u1", List.of("v1", "v2"), "u2", List.of("v1"), "u3", List.of("v2", "v3"))));
    }
}

