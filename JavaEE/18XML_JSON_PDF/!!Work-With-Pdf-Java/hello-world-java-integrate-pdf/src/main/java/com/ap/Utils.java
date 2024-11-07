package com.ap;

import java.util.*;
import java.util.stream.Collectors;

public class Utils {

	public static <T, U> T sumOfPrimes(U input) {
		if (!(input instanceof Integer)) {
			throw new IllegalArgumentException("Input must be an Integer");
		}
		int n = (Integer) input;
		int sum = 0;
		for (int i = 2; i <= n; i++) {
			if (isPrime(i)) {
				sum += i;
			}
		}
		return (T) Integer.valueOf(sum);
	}

	private static boolean isPrime(int num) {
		if (num <= 1) return false;
		for (int i = 2; i <= Math.sqrt(num); i++) {
			if (num % i == 0) return false;
		}
		return true;
	}

	public static <T> T convertToTitleCase(T input) {
		if (!(input instanceof String)) {
			throw new IllegalArgumentException("Input must be a String");
		}
		String str = (String) input;
		String result = Arrays.stream(str.split(" "))
				.map(word -> word.isEmpty() ? word :
						Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
				.collect(Collectors.joining(" "));
		return (T) result;
	}

	public static <T, U> T groupAnagrams(U arr) {
		if (!(arr instanceof String[])) {
			throw new IllegalArgumentException("Input must be an array of Strings");
		}
		String[] strs = (String[]) arr;
		Map<String, List<String>> anagrams = new HashMap<>();
		for (String s : strs) {
			char[] chars = s.toCharArray();
			Arrays.sort(chars);
			String key = new String(chars);
			anagrams.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
		}
		return (T) new ArrayList<>(anagrams.values());
	}
}