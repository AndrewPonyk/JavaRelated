package graphcoloring;

import java.util.*;

public class GraphColoring {

    private final int[][] adj;
    private final int numColors;
    private final int v;
    private final int[] colors;

    public GraphColoring(int[][] adj, int numColors) {
        this.adj = adj;
        this.numColors = numColors;
        this.v = adj.length;
        this.colors = new int[v];
        Arrays.fill(colors, -1);
    }

    public void solveAndPrint() {
        System.out.printf("Graph with %d vertices, %d colors%n", v, numColors);
        System.out.println("Adjacency matrix:");
        for (int[] row : adj) System.out.println(Arrays.toString(row));
        System.out.println();

        boolean success = backtrack(0);

        if (success) {
            System.out.println("Valid coloring found:");
            String[] names = {"Red", "Green", "Blue", "Yellow", "Purple", "Orange", "Cyan", "White"};
            for (int i = 0; i < v; i++) {
                String colorName = colors[i] < names.length ? names[colors[i]] : "C" + colors[i];
                System.out.printf("  Vertex %d → %s%n", i, colorName);
            }
        } else {
            System.out.println("No valid coloring exists with " + numColors + " colors.");
        }
    }

    private boolean backtrack(int vertex) {
        if (vertex == v) return true;

        for (int c = 0; c < numColors; c++) {
            if (isSafe(vertex, c)) {
                colors[vertex] = c;
                if (backtrack(vertex + 1)) return true;
                colors[vertex] = -1;
            }
        }
        return false;
    }

    private boolean isSafe(int vertex, int color) {
        for (int i = 0; i < v; i++) {
            if (adj[vertex][i] == 1 && colors[i] == color) return false;
        }
        return true;
    }
}
