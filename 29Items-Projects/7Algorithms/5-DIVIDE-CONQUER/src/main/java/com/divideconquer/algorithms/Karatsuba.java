package com.divideconquer.algorithms;

public final class Karatsuba {
    private Karatsuba() {
    }

    public static long multiply(long x, long y) {
        if (x < 0 || y < 0) {
            boolean negative = (x < 0) ^ (y < 0);
            long result = multiply(Math.abs(x), Math.abs(y));
            return negative ? -result : result;
        }
        if (x < 10 || y < 10) {
            return x * y;
        }

        int digits = Math.max(decimalDigits(x), decimalDigits(y));
        int half = digits / 2;
        long base = pow10(half);

        long highX = x / base;
        long lowX = x % base;
        long highY = y / base;
        long lowY = y % base;

        long z0 = multiply(lowX, lowY);
        long z2 = multiply(highX, highY);
        long z1 = multiply(lowX + highX, lowY + highY) - z2 - z0;

        return z2 * base * base + z1 * base + z0;
    }

    private static int decimalDigits(long value) {
        int digits = 1;
        while (value >= 10) {
            value /= 10;
            digits++;
        }
        return digits;
    }

    private static long pow10(int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }
        return result;
    }
}
