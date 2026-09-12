package com.divideconquer.algorithms;

import java.util.Arrays;

public final class PolynomialMultiplication {
    private PolynomialMultiplication() {
    }

    public static int[] multiply(int[] a, int[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            throw new IllegalArgumentException("polynomials must not be empty");
        }
        int resultSize = a.length + b.length - 1;
        int size = nextPowerOfTwo(Math.max(a.length, b.length));
        int[] paddedA = Arrays.copyOf(a, size);
        int[] paddedB = Arrays.copyOf(b, size);
        return Arrays.copyOf(multiplyEqualSize(paddedA, paddedB), resultSize);
    }

    private static int[] multiplyEqualSize(int[] a, int[] b) {
        int n = a.length;
        if (n == 1) {
            return new int[]{a[0] * b[0]};
        }

        int half = n / 2;
        int[] lowA = Arrays.copyOfRange(a, 0, half);
        int[] highA = Arrays.copyOfRange(a, half, n);
        int[] lowB = Arrays.copyOfRange(b, 0, half);
        int[] highB = Arrays.copyOfRange(b, half, n);

        int[] z0 = multiplyEqualSize(lowA, lowB);
        int[] z2 = multiplyEqualSize(highA, highB);
        int[] z1 = subtract(subtract(multiplyEqualSize(add(lowA, highA), add(lowB, highB)), z0), z2);

        int[] result = new int[2 * n - 1];
        addInto(result, z0, 0);
        addInto(result, z1, half);
        addInto(result, z2, 2 * half);
        return result;
    }

    private static int[] add(int[] a, int[] b) {
        int[] result = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] + b[i];
        }
        return result;
    }

    private static int[] subtract(int[] a, int[] b) {
        int[] result = Arrays.copyOf(a, Math.max(a.length, b.length));
        for (int i = 0; i < b.length; i++) {
            result[i] -= b[i];
        }
        return result;
    }

    private static void addInto(int[] target, int[] values, int offset) {
        for (int i = 0; i < values.length; i++) {
            target[offset + i] += values[i];
        }
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result *= 2;
        }
        return result;
    }
}
