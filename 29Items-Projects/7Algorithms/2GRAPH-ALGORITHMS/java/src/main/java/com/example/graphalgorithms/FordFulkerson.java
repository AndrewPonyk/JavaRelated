package com.example.graphalgorithms;

import java.util.*;

public class FordFulkerson {
    public static int maxFlow(int[][] capacity, int source, int sink) {
        validate(capacity, source, sink);
        int[][] residual = copy(capacity);
        int maxFlow = 0;
        int[] parent = new int[capacity.length];
        while (dfs(residual, source, sink, new boolean[capacity.length], parent)) {
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

    private static boolean dfs(int[][] residual, int current, int sink, boolean[] visited, int[] parent) {
        visited[current] = true;
        if (current == sink) {
            return true;
        }
        for (int next = 0; next < residual.length; next++) {
            if (!visited[next] && residual[current][next] > 0) {
                parent[next] = current;
                if (dfs(residual, next, sink, visited, parent)) {
                    return true;
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
        System.out.println("Ford-Fulkerson max flow");
        System.out.println(maxFlow(capacity, 0, 5));
    }
}
