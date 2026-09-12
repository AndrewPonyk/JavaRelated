package hamiltonian;

import java.util.*;

public class HamiltonianPath {

    private final int[][] adj;
    private final int v;
    private final int[] path;
    private boolean found = false;

    public HamiltonianPath(int[][] adj) {
        this.adj = adj;
        this.v = adj.length;
        this.path = new int[v];
        Arrays.fill(path, -1);
    }

    public void solveAndPrint() {
        path[0] = 0;
        System.out.println("Graph adjacency matrix:");
        printMatrix();
        System.out.println();

        found = backtrack(1);

        if (found) {
            System.out.print("Hamiltonian Path: ");
            for (int i = 0; i < v; i++) {
                System.out.print(path[i] + (i < v - 1 ? " → " : ""));
            }
            System.out.println();
        } else {
            System.out.println("No Hamiltonian Path exists.");
        }
    }

    private boolean backtrack(int pos) {
        if (pos == v) return true;

        for (int next = 0; next < v; next++) {
            if (isSafe(next, pos)) {
                path[pos] = next;
                if (backtrack(pos + 1)) return true;
                path[pos] = -1;
            }
        }
        return false;
    }

    private boolean isSafe(int vertex, int pos) {
        if (adj[path[pos - 1]][vertex] == 0) return false;
        for (int i = 0; i < pos; i++) {
            if (path[i] == vertex) return false;
        }
        return true;
    }

    private void printMatrix() {
        for (int[] row : adj) {
            System.out.println(Arrays.toString(row));
        }
    }
}
