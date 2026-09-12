package cryptarithmetic;

import java.util.*;

public class Cryptarithmetic {

    private final String word1;
    private final String word2;
    private final String result;
    private final List<Character> uniqueLetters;
    private final Map<Character, Integer> assignment = new HashMap<>();
    private final List<Map<Character, Integer>> solutions = new ArrayList<>();

    public Cryptarithmetic(String word1, String word2, String result) {
        this.word1 = word1.toUpperCase();
        this.word2 = word2.toUpperCase();
        this.result = result.toUpperCase();

        Set<Character> letters = new LinkedHashSet<>();
        for (char c : this.word1.toCharArray()) letters.add(c);
        for (char c : this.word2.toCharArray()) letters.add(c);
        for (char c : this.result.toCharArray()) letters.add(c);
        this.uniqueLetters = new ArrayList<>(letters);
    }

    public void solveAndPrint() {
        System.out.printf("Puzzle: %s + %s = %s%n", word1, word2, result);
        System.out.printf("Unique letters: %s%n%n", uniqueLetters);

        boolean[] usedDigits = new boolean[10];
        backtrack(0, usedDigits);

        if (solutions.isEmpty()) {
            System.out.println("No solution found.");
        } else {
            System.out.printf("Found %d solution(s):%n%n", solutions.size());
            for (int i = 0; i < solutions.size(); i++) {
                Map<Character, Integer> sol = solutions.get(i);
                System.out.printf("Solution #%d:%n", i + 1);
                System.out.printf("  %s%n", formatWord(word1, sol));
                System.out.printf("+ %s%n", formatWord(word2, sol));
                System.out.printf("= %s%n%n", formatWord(result, sol));
            }
        }
    }

    private void backtrack(int letterIndex, boolean[] usedDigits) {
        if (letterIndex == uniqueLetters.size()) {
            if (checkEquation()) {
                solutions.add(new HashMap<>(assignment));
            }
            return;
        }

        char letter = uniqueLetters.get(letterIndex);
        boolean isFirstLetter = (letter == word1.charAt(0))
                || (letter == word2.charAt(0))
                || (letter == result.charAt(0));

        int start = isFirstLetter ? 1 : 0;
        for (int digit = start; digit <= 9; digit++) {
            if (!usedDigits[digit]) {
                assignment.put(letter, digit);
                usedDigits[digit] = true;
                backtrack(letterIndex + 1, usedDigits);
                assignment.remove(letter);
                usedDigits[digit] = false;
            }
        }
    }

    private boolean checkEquation() {
        long val1 = wordToNumber(word1);
        long val2 = wordToNumber(word2);
        long res = wordToNumber(result);
        return val1 + val2 == res;
    }

    private long wordToNumber(String word) {
        long num = 0;
        for (char c : word.toCharArray()) {
            num = num * 10 + assignment.get(c);
        }
        return num;
    }

    private String formatWord(String word, Map<Character, Integer> sol) {
        StringBuilder sb = new StringBuilder();
        for (char c : word.toCharArray()) {
            sb.append(sol.get(c));
        }
        return sb.toString();
    }
}
