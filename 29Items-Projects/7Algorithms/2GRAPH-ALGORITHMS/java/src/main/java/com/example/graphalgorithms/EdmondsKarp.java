package com.example.graphalgorithms;

import java.util.*;

public class EdmondsKarp {
    public static int maxFlow(int[][] capacity, int source, int sink) {
        validate(capacity, source, sink);
        int[][] residual = copy(capacity);
        int maxFlow = 0;
        int[] parent = new int[capacity.length];
        while (bfs(residual, source, sink, parent)) {
            int pathFlow = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = parent[v]) {
                pathFlow = Math.min(pathFlow, residual[parent[v]][v]);
            }
            for (int v = sink; v != source; v = parent[v]) {
                residual[parent[v]][v] -= pathFlow;
                residual[v][parent[v]] += pathFlow;
            }
            maxFlow += pathFlow;
        }
        return maxFlow;
    }

    private static boolean bfs(int[][] residual, int source, int sink, int[] parent) {
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[residual.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        visited[source] = true;
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int next = 0; next < residual.length; next++) {
                if (!visited[next] && residual[current][next] > 0) {
                    parent[next] = current;
                    if (next == sink) {
                        return true;
                    }
                    visited[next] = true;
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static int[][] copy(int[][] matrix) {
        int[][] result = new int[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            result[i] = Arrays.copyOf(matrix[i], matrix[i].length);
        }
        return result;
    }

    private static void validate(int[][] capacity, int source, int sink) {
        if (capacity.length == 0) {
            throw new IllegalArgumentException("capacity matrix is required");
        }
        if (source == sink || source < 0 || sink < 0 || source >= capacity.length || sink >= capacity.length) {
            throw new IllegalArgumentException("source and sink must be valid distinct indexes");
        }
        for (int[] row : capacity) {
            if (row.length != capacity.length) {
                throw new IllegalArgumentException("capacity matrix must be square");
            }
            for (int value : row) {
                if (value < 0) {
                    throw new IllegalArgumentException("capacities must be non-negative");
                }
            }
        }
    }

    public static void main(String[] args) {
        int[][] capacity = {
            {0, 16, 13, 0, 0, 0},
            {0, 0, 10, 12, 0, 0},
            {0, 4, 0, 0, 14, 0},
            {0, 0, 9, 0, 0, 20},
            {0, 0, 0, 7, 0, 4},
            {0, 0, 0, 0, 0, 0}
        };
        System.out.println("Edmonds-Karp max flow");
        System.out.println(maxFlow(capacity, 0, 5));
    }
}
