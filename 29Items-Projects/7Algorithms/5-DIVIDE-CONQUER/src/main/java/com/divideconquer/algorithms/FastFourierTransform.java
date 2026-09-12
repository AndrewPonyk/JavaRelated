package com.divideconquer.algorithms;

import java.util.Arrays;

public final class FastFourierTransform {
    private FastFourierTransform() {
    }

    public static final class Complex {
        private final double real;
        private final double imaginary;

        public Complex(double real, double imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }

        public double real() {
            return real;
        }

        public double imaginary() {
            return imaginary;
        }

        public Complex plus(Complex other) {
            return new Complex(real + other.real, imaginary + other.imaginary);
        }

        public Complex minus(Complex other) {
            return new Complex(real - other.real, imaginary - other.imaginary);
        }

        public Complex times(Complex other) {
            return new Complex(
                    real * other.real - imaginary * other.imaginary,
                    real * other.imaginary + imaginary * other.real
            );
        }

        @Override
        public String toString() {
            return String.format("%.2f%+.2fi", real, imaginary);
        }
    }

    public static Complex[] fft(Complex[] input) {
        if (input == null || input.length == 0 || (input.length & (input.length - 1)) != 0) {
            throw new IllegalArgumentException("input length must be a non-empty power of two");
        }
        if (Arrays.stream(input).anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("input must not contain null values");
        }
        if (input.length == 1) {
            return Arrays.copyOf(input, 1);
        }

        int n = input.length;
        Complex[] even = new Complex[n / 2];
        Complex[] odd = new Complex[n / 2];
        for (int i = 0; i < n / 2; i++) {
            even[i] = input[2 * i];
            odd[i] = input[2 * i + 1];
        }

        Complex[] evenFft = fft(even);
        Complex[] oddFft = fft(odd);
        Complex[] result = new Complex[n];

        for (int k = 0; k < n / 2; k++) {
            double angle = -2.0 * Math.PI * k / n;
            Complex twiddle = new Complex(Math.cos(angle), Math.sin(angle));
            Complex term = twiddle.times(oddFft[k]);
            result[k] = evenFft[k].plus(term);
            result[k + n / 2] = evenFft[k].minus(term);
        }
        return result;
    }

    public static Complex[] inverseFft(Complex[] input) {
        validate(input);
        Complex[] conjugated = new Complex[input.length];
        for (int i = 0; i < input.length; i++) {
            conjugated[i] = new Complex(input[i].real(), -input[i].imaginary());
        }
        Complex[] transformed = fft(conjugated);
        Complex[] result = new Complex[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = new Complex(transformed[i].real() / input.length, -transformed[i].imaginary() / input.length);
        }
        return result;
    }

    public static double[] convolution(double[] first, double[] second) {
        if (first == null || second == null || first.length == 0 || second.length == 0) {
            throw new IllegalArgumentException("signals must not be empty");
        }
        int resultSize = first.length + second.length - 1;
        int size = 1;
        while (size < resultSize) {
            size *= 2;
        }
        Complex[] a = new Complex[size];
        Complex[] b = new Complex[size];
        for (int i = 0; i < size; i++) {
            a[i] = new Complex(i < first.length ? first[i] : 0, 0);
            b[i] = new Complex(i < second.length ? second[i] : 0, 0);
        }
        Complex[] fa = fft(a);
        Complex[] fb = fft(b);
        Complex[] product = new Complex[size];
        for (int i = 0; i < size; i++) {
            product[i] = fa[i].times(fb[i]);
        }
        Complex[] inverse = inverseFft(product);
        double[] result = new double[resultSize];
        for (int i = 0; i < resultSize; i++) {
            result[i] = inverse[i].real();
        }
        return result;
    }

    private static void validate(Complex[] input) {
        if (input == null || input.length == 0 || (input.length & (input.length - 1)) != 0) {
            throw new IllegalArgumentException("input length must be a non-empty power of two");
        }
        if (Arrays.stream(input).anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("input must not contain null values");
        }
    }
}
