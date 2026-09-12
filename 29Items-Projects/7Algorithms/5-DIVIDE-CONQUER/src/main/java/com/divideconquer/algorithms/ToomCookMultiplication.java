package com.divideconquer.algorithms;

import java.math.BigInteger;

public final class ToomCookMultiplication {
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger THREE = BigInteger.valueOf(3);
    private ToomCookMultiplication() {
    }

    public static BigInteger multiply(BigInteger x, BigInteger y) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("values must not be null");
        }
        if (x.signum() < 0 || y.signum() < 0) {
            BigInteger result = multiply(x.abs(), y.abs());
            return x.signum() * y.signum() < 0 ? result.negate() : result;
        }
        if (x.bitLength() < 32 || y.bitLength() < 32) {
            return x.multiply(y);
        }

        int split = Math.max(decimalDigits(x), decimalDigits(y));
        int partSize = (split + 2) / 3;
        BigInteger base = BigInteger.TEN.pow(partSize);
        BigInteger baseSquared = base.multiply(base);

        BigInteger a0 = x.mod(base);
        BigInteger a1 = x.divide(base).mod(base);
        BigInteger a2 = x.divide(baseSquared);
        BigInteger b0 = y.mod(base);
        BigInteger b1 = y.divide(base).mod(base);
        BigInteger b2 = y.divide(baseSquared);

        BigInteger p0 = multiply(a0, b0);
        BigInteger p1 = multiply(a0.add(a1).add(a2), b0.add(b1).add(b2));
        BigInteger pm1 = multiply(a0.subtract(a1).add(a2), b0.subtract(b1).add(b2));
        BigInteger p2 = multiply(a0.add(a1.multiply(TWO)).add(a2.multiply(BigInteger.valueOf(4))),
                b0.add(b1.multiply(TWO)).add(b2.multiply(BigInteger.valueOf(4))));
        BigInteger pinf = multiply(a2, b2);

        BigInteger c0 = p0;
        BigInteger c4 = pinf;
        BigInteger s1 = p1.subtract(c0).subtract(c4);
        BigInteger sm1 = pm1.subtract(c0).subtract(c4);
        BigInteger s2 = p2.subtract(c0).subtract(c4.multiply(BigInteger.valueOf(16)));
        BigInteger c2 = s1.add(sm1).divide(TWO);
        BigInteger c1PlusC3 = s1.subtract(sm1).divide(TWO);
        BigInteger c3 = s2.subtract(c2.multiply(BigInteger.valueOf(4))).subtract(c1PlusC3.multiply(TWO)).divide(BigInteger.valueOf(6));
        BigInteger c1 = c1PlusC3.subtract(c3);

        return c0
                .add(c1.multiply(base))
                .add(c2.multiply(base.pow(2)))
                .add(c3.multiply(base.pow(3)))
                .add(c4.multiply(base.pow(4)));
    }

    private static int decimalDigits(BigInteger value) {
        return value.toString().length();
    }
}
