package com.ap;

public class PermutationsOfString {
    public static void main(String[] args) {
        System.out.println("test");

        String s = "abcde";
        permutation(s);

    }

    private static void permutation(String input) {
        permutation("", input);
    }

    private static void permutation(String permutatioin, String word) {
        if (word.isEmpty()) {
            System.out.println(permutatioin);
        } else {
            for (int i = 0; i < word.length(); i++) {
                permutation(permutatioin + word.charAt(i),
                        word.substring(0, i) + word.substring(i + 1));
            }
        }
    }
}
