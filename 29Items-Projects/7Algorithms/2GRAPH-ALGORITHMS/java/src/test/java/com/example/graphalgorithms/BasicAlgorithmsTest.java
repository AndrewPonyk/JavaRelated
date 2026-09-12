package com.example.graphalgorithms;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BasicAlgorithmsTest {
    @Test
    void dijkstraFindsShortestPath() {
        Map<String, List<Dijkstra.Edge>> graph = Map.of(
            "A", List.of(new Dijkstra.Edge("B", 4), new Dijkstra.Edge("C", 2)),
            "B", List.of(new Dijkstra.Edge("D", 1)),
            "C", List.of(new Dijkstra.Edge("B", 1), new Dijkstra.Edge("D", 5)),
            "D", List.of()
        );
        assertEquals(4, Dijkstra.shortestPaths(graph, "A").get("D"));
    }

    @Test
    void bellmanFordHandlesNegativeEdge() {
        Map<String, Integer> result = BellmanFord.shortestPaths(
            List.of("A", "B", "C"),
            List.of(new BellmanFord.Edge("A", "B", 4), new BellmanFord.Edge("A", "C", 5), new BellmanFord.Edge("B", "C", -2)),
            "A"
        );
        assertEquals(2, result.get("C"));
    }

    @Test
    void topologicalSortOrdersDependencies() {
        List<String> order = TopologicalSort.sort(Map.of("plan", List.of("code"), "code", List.of("test"), "test", List.of()));
        assertTrue(order.indexOf("plan") < order.indexOf("code"));
        assertTrue(order.indexOf("code") < order.indexOf("test"));
    }

    @Test
    void kruskalFindsExpectedWeight() {
        Kruskal.Result result = Kruskal.minimumSpanningTree(
            List.of("A", "B", "C", "D"),
            List.of(new Kruskal.Edge("A", "B", 1), new Kruskal.Edge("A", "C", 4), new Kruskal.Edge("B", "C", 2), new Kruskal.Edge("C", "D", 1))
        );
        assertEquals(4, result.totalWeight());
    }

    @Test
    void edmondsKarpFindsMaxFlow() {
        int[][] capacity = {
            {0, 16, 13, 0, 0, 0},
            {0, 0, 10, 12, 0, 0},
            {0, 4, 0, 0, 14, 0},
            {0, 0, 9, 0, 0, 20},
            {0, 0, 0, 7, 0, 4},
            {0, 0, 0, 0, 0, 0}
        };
        assertEquals(23, EdmondsKarp.maxFlow(capacity, 0, 5));
    }
}
