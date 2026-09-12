package com.divideconquer.algorithms;

public final class StrassenMatrix {
    private StrassenMatrix() {
    }

    public static int[][] multiply(int[][] a, int[][] b) {
        validate(a, b);
        int n = a.length;
        int paddedSize = nextPowerOfTwo(n);
        if (paddedSize != n) {
            int[][] result = multiplyPowerOfTwo(pad(a, paddedSize), pad(b, paddedSize));
            return trim(result, n);
        }
        return multiplyPowerOfTwo(a, b);
    }

    private static int[][] multiplyPowerOfTwo(int[][] a, int[][] b) {
        int n = a.length;
        if (n == 1) {
            return new int[][]{{a[0][0] * b[0][0]}};
        }

        int half = n / 2;
        int[][] a11 = slice(a, 0, 0, half);
        int[][] a12 = slice(a, 0, half, half);
        int[][] a21 = slice(a, half, 0, half);
        int[][] a22 = slice(a, half, half, half);
        int[][] b11 = slice(b, 0, 0, half);
        int[][] b12 = slice(b, 0, half, half);
        int[][] b21 = slice(b, half, 0, half);
        int[][] b22 = slice(b, half, half, half);

        int[][] p1 = multiplyPowerOfTwo(add(a11, a22), add(b11, b22));
        int[][] p2 = multiplyPowerOfTwo(add(a21, a22), b11);
        int[][] p3 = multiplyPowerOfTwo(a11, subtract(b12, b22));
        int[][] p4 = multiplyPowerOfTwo(a22, subtract(b21, b11));
        int[][] p5 = multiplyPowerOfTwo(add(a11, a12), b22);
        int[][] p6 = multiplyPowerOfTwo(subtract(a21, a11), add(b11, b12));
        int[][] p7 = multiplyPowerOfTwo(subtract(a12, a22), add(b21, b22));

        int[][] c11 = add(subtract(add(p1, p4), p5), p7);
        int[][] c12 = add(p3, p5);
        int[][] c21 = add(p2, p4);
        int[][] c22 = add(subtract(add(p1, p3), p2), p6);

        return join(c11, c12, c21, c22);
    }

    private static void validate(int[][] a, int[][] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            throw new IllegalArgumentException("matrices must be non-empty and have the same square size");
        }
        int n = a.length;
        for (int i = 0; i < n; i++) {
            if (a[i] == null || b[i] == null || a[i].length != n || b[i].length != n) {
                throw new IllegalArgumentException("matrices must be square");
            }
        }
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result *= 2;
        }
        return result;
    }

    private static int[][] pad(int[][] matrix, int size) {
        int[][] result = new int[size][size];
        for (int row = 0; row < matrix.length; row++) {
            System.arraycopy(matrix[row], 0, result[row], 0, matrix.length);
        }
        return result;
    }

    private static int[][] trim(int[][] matrix, int size) {
        int[][] result = new int[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[row], 0, result[row], 0, size);
        }
        return result;
    }

    private static int[][] add(int[][] a, int[][] b) {
        int n = a.length;
        int[][] result = new int[n][n];
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                result[row][col] = a[row][col] + b[row][col];
            }
        }
        return result;
    }

    private static int[][] subtract(int[][] a, int[][] b) {
        int n = a.length;
        int[][] result = new int[n][n];
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                result[row][col] = a[row][col] - b[row][col];
            }
        }
        return result;
    }

    private static int[][] slice(int[][] matrix, int startRow, int startCol, int size) {
        int[][] result = new int[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[startRow + row], startCol, result[row], 0, size);
        }
        return result;
    }

    private static int[][] join(int[][] c11, int[][] c12, int[][] c21, int[][] c22) {
        int half = c11.length;
        int[][] result = new int[half * 2][half * 2];
        for (int row = 0; row < half; row++) {
            System.arraycopy(c11[row], 0, result[row], 0, half);
            System.arraycopy(c12[row], 0, result[row], half, half);
            System.arraycopy(c21[row], 0, result[half + row], 0, half);
            System.arraycopy(c22[row], 0, result[half + row], half, half);
        }
        return result;
    }
}
