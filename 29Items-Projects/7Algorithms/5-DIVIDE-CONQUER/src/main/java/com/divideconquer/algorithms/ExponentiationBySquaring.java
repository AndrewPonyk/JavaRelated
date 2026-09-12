package com.divideconquer.algorithms;

public final class ExponentiationBySquaring {
    private ExponentiationBySquaring() {
    }

    public static long pow(long base, int exponent) {
        if (exponent < 0) {
            throw new IllegalArgumentException("exponent must be non-negative");
        }
        if (exponent == 0) {
            return 1;
        }
        long half = pow(base, exponent / 2);
        long squared = half * half;
        return exponent % 2 == 0 ? squared : squared * base;
    }
}
