package com.example.graphalgorithms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AllAlgorithmsTest {
    @Test
    void appRunsEveryAlgorithmByName() {
        List<String> algorithms = List.of(
            "astar",
            "articulation-points",
            "bellman-ford",
            "bipartite-matching",
            "bridges",
            "dijkstra",
            "edmonds-karp",
            "floyd-warshall",
            "ford-fulkerson",
            "kosaraju-scc",
            "kruskal",
            "prim",
            "tarjan-scc",
            "topological-sort"
        );
        for (String algorithm : algorithms) {
            assertNotNull(App.runAlgorithm(algorithm));
        }
    }

    @Test
    void astarFindsExpectedPath() {
        Map<String, List<AStar.Edge>> graph = Map.of(
            "A", List.of(new AStar.Edge("B", 1), new AStar.Edge("C", 4)),
            "B", List.of(new AStar.Edge("D", 2)),
            "C", List.of(new AStar.Edge("D", 1)),
            "D", List.of()
        );
        AStar.Result result = AStar.search(graph, "A", "D", (node, goal) -> 0);
        assertEquals(3, result.cost());
        assertEquals(List.of("A", "B", "D"), result.path());
    }

    @Test
    void floydWarshallFindsTransitiveShortestPath() {
        Map<String, Map<String, Integer>> result = FloydWarshall.allPairs(
            List.of("A", "B", "C"),
            List.of(new FloydWarshall.Edge("A", "B", 3), new FloydWarshall.Edge("B", "C", 1), new FloydWarshall.Edge("A", "C", 10))
        );
        assertEquals(4, result.get("A").get("C"));
    }

    @Test
    void sccAlgorithmsFindCycleComponent() {
        Map<String, List<String>> graph = Map.of("A", List.of("B"), "B", List.of("C"), "C", List.of("A", "D"), "D", List.of());
        assertTrue(TarjanScc.find(graph).stream().anyMatch(component -> component.containsAll(List.of("A", "B", "C"))));
        assertTrue(KosarajuScc.find(graph).stream().anyMatch(component -> component.containsAll(List.of("A", "B", "C"))));
    }

    @Test
    void articulationPointsAndBridgesAreDetected() {
        Map<String, List<String>> graph = Map.of("A", List.of("B"), "B", List.of("A", "C", "D"), "C", List.of("B"), "D", List.of("B"));
        assertEquals(Set.of("B"), ArticulationPoints.find(graph));
        assertEquals(3, Bridges.find(graph).size());
    }

    @Test
    void bipartiteMatchingCoversLeftSide() {
        Map<String, String> result = BipartiteMatching.maximumMatching(Map.of(
            "u1", List.of("v1", "v2"),
            "u2", List.of("v1"),
            "u3", List.of("v2", "v3")
        ));
        assertEquals(3, result.size());
    }

    @Test
    void algorithmsRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Dijkstra.shortestPaths(Map.of("A", List.of()), "B"));
        assertThrows(IllegalArgumentException.class, () -> AStar.search(Map.of("A", List.of()), "A", "B", (node, goal) -> 0));
        assertThrows(IllegalArgumentException.class, () -> BellmanFord.shortestPaths(List.of("A", "A"), List.of(), "A"));
        assertThrows(IllegalArgumentException.class, () -> FloydWarshall.allPairs(List.of("A"), List.of(new FloydWarshall.Edge("A", "B", 1))));
        assertThrows(IllegalArgumentException.class, () -> Kruskal.minimumSpanningTree(List.of("A", "B"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.maxFlow(new int[][] {{0, 1}}, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> FordFulkerson.maxFlow(new int[][] {{0, -1}, {0, 0}}, 0, 1));
    }
}
