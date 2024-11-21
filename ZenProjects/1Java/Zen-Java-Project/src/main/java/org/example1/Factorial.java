package org.example1;

public class Factorial {
	public static void main(String[] args) {
		System.out.println(factorial(5));
	}

	public static long factorial(int n) {
		if (n < 0) {
			throw new IllegalArgumentException("n must be >= 0");
		}
		if (n == 0) {
			return 1;
		}
		return n * factorial(n - 1);
	}
}

