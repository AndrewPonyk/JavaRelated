package com.example.graphalgorithms;

import java.util.*;

public class App {
    private static final List<String> ALGORITHMS = List.of(
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

    public static void main(String[] args) {
        if (args.length == 0 || "run-all".equals(args[0])) {
            runAll();
            return;
        }
        if ("list".equals(args[0])) {
            ALGORITHMS.forEach(System.out::println);
            return;
        }
        if ("run".equals(args[0])) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Usage: App run <algorithm>");
            }
            System.out.println(args[1] + ": " + runAlgorithm(args[1]));
            return;
        }
        if ("benchmark".equals(args[0])) {
            benchmarkDijkstra();
            return;
        }
        throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    public static void runAll() {
        System.out.println("Graph Algorithms Java Demo");
        for (String algorithm : ALGORITHMS) {
            System.out.println(algorithm + ": " + runAlgorithm(algorithm));
        }
    }

    public static Object runAlgorithm(String name) {
        switch (name) {
            case "dijkstra":
                return Dijkstra.shortestPaths(dijkstraGraph(), "A");
            case "astar":
                return AStar.search(astarGraph(), "A", "D", (node, goal) -> 0);
            case "floyd-warshall":
                return FloydWarshall.allPairs(weightedVertices(), weightedEdges());
            case "bellman-ford":
                return BellmanFord.shortestPaths(weightedVertices(), bellmanEdges(), "A");
            case "prim":
                return Prim.minimumSpanningTree(primGraph(), "A");
            case "kruskal":
                return Kruskal.minimumSpanningTree(mstVertices(), kruskalEdges());
            case "topological-sort":
                return TopologicalSort.sort(dag());
            case "tarjan-scc":
                return TarjanScc.find(sccGraph());
            case "kosaraju-scc":
                return KosarajuScc.find(sccGraph());
            case "articulation-points":
                return ArticulationPoints.find(undirectedUnweightedGraph());
            case "bridges":
                return Bridges.find(undirectedUnweightedGraph());
            case "ford-fulkerson":
                return FordFulkerson.maxFlow(capacityMatrix(), 0, 5);
            case "edmonds-karp":
                return EdmondsKarp.maxFlow(capacityMatrix(), 0, 5);
            case "bipartite-matching":
                return BipartiteMatching.maximumMatching(bipartiteGraph());
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + name);
        }
    }

    private static void benchmarkDijkstra() {
        Map<String, List<Dijkstra.Edge>> graph = new HashMap<>();
        int size = 100;
        for (int i = 0; i < size; i++) {
            graph.put(Integer.toString(i), new ArrayList<>());
        }
        for (int i = 0; i < size - 1; i++) {
            graph.get(Integer.toString(i)).add(new Dijkstra.Edge(Integer.toString(i + 1), 1));
        }
        long started = System.nanoTime();
        Dijkstra.shortestPaths(graph, "0");
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf(Locale.US, "dijkstra benchmark: %.3f ms%n", elapsedMs);
    }

    private static Map<String, List<Dijkstra.Edge>> dijkstraGraph() {
        return Map.of(
            "A", List.of(new Dijkstra.Edge("B", 4), new Dijkstra.Edge("C", 2)),
            "B", List.of(new Dijkstra.Edge("D", 1)),
            "C", List.of(new Dijkstra.Edge("B", 1), new Dijkstra.Edge("D", 5)),
            "D", List.of()
        );
    }

    private static Map<String, List<AStar.Edge>> astarGraph() {
        return Map.of(
            "A", List.of(new AStar.Edge("B", 1), new AStar.Edge("C", 4)),
            "B", List.of(new AStar.Edge("D", 2)),
            "C", List.of(new AStar.Edge("D", 1)),
            "D", List.of()
        );
    }

    private static List<String> weightedVertices() {
        return List.of("A", "B", "C", "D", "E");
    }

    private static List<FloydWarshall.Edge> weightedEdges() {
        return List.of(
            new FloydWarshall.Edge("A", "B", 4),
            new FloydWarshall.Edge("A", "C", 2),
            new FloydWarshall.Edge("B", "C", 1),
            new FloydWarshall.Edge("B", "D", 5),
            new FloydWarshall.Edge("D", "E", 2)
        );
    }

    private static List<BellmanFord.Edge> bellmanEdges() {
        return List.of(
            new BellmanFord.Edge("A", "B", 4),
            new BellmanFord.Edge("A", "C", 5),
            new BellmanFord.Edge("B", "C", -2),
            new BellmanFord.Edge("C", "D", 3),
            new BellmanFord.Edge("D", "E", 2)
        );
    }

    private static Map<String, List<Prim.Edge>> primGraph() {
        return Map.of(
            "A", List.of(new Prim.Edge("B", 1), new Prim.Edge("C", 4)),
            "B", List.of(new Prim.Edge("A", 1), new Prim.Edge("C", 2), new Prim.Edge("D", 5)),
            "C", List.of(new Prim.Edge("A", 4), new Prim.Edge("B", 2), new Prim.Edge("D", 1)),
            "D", List.of(new Prim.Edge("B", 5), new Prim.Edge("C", 1))
        );
    }

    private static List<String> mstVertices() {
        return List.of("A", "B", "C", "D");
    }

    private static List<Kruskal.Edge> kruskalEdges() {
        return List.of(
            new Kruskal.Edge("A", "B", 1),
            new Kruskal.Edge("A", "C", 4),
            new Kruskal.Edge("B", "C", 2),
            new Kruskal.Edge("B", "D", 5),
            new Kruskal.Edge("C", "D", 1)
        );
    }

    private static Map<String, List<String>> dag() {
        return Map.of("plan", List.of("code"), "code", List.of("test"), "test", List.of("ship"), "ship", List.of());
    }

    private static Map<String, List<String>> sccGraph() {
        return Map.of("A", List.of("B"), "B", List.of("C"), "C", List.of("A", "D"), "D", List.of());
    }

    private static Map<String, List<String>> undirectedUnweightedGraph() {
        return Map.of("A", List.of("B"), "B", List.of("A", "C", "D"), "C", List.of("B"), "D", List.of("B"));
    }

    private static Map<String, List<String>> bipartiteGraph() {
        return Map.of("u1", List.of("v1", "v2"), "u2", List.of("v1"), "u3", List.of("v2", "v3"));
    }

    private static int[][] capacityMatrix() {
        return new int[][] {
            {0, 16, 13, 0, 0, 0},
            {0, 0, 10, 12, 0, 0},
            {0, 4, 0, 0, 14, 0},
            {0, 0, 9, 0, 0, 20},
            {0, 0, 0, 7, 0, 4},
            {0, 0, 0, 0, 0, 0}
        };
    }
}
