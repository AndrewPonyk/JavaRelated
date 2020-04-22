package com.ap;

import java.util.Arrays;
import java.util.List;

public class StringSimilarityUtils {


    public static void main(String[] args) {
        System.out.println(sumOfNumbersInString("1-2(7-11,8-11,11-4,9-8)"));

        System.out.println(wordEqual("andrii", "andriyia"));

        System.out.println(stringsEqualsByWords(
                "Shirshov Vasily - Pandur Ivan",
                "Shirshov Vasil - Pandur Ivan"));
    }


    public static boolean stringsEqualsByWords(String s1, String s2) {
        List<String> words1 = Arrays.asList(s1.trim().toLowerCase().split("\\W+"));

        List<String> words2 = Arrays.asList(s2.trim().toLowerCase().split("\\W+"));

        if (s1.trim().isEmpty() || s2.trim().isEmpty()) {
            return false;
        }

        if (Math.abs(words1.size() - words2.size()) > 1) {
            return false;
        }

        for (String word1 : words1) {
            boolean passed = false;
            for (String word2 : words2) {
                passed = wordEqual(word1, word2);
                if (passed) break;
            }

            if (!passed) {
                return false;
            }
        }


        return true;
    }

    /**
     * Words are 'equal' if 2 rules are true
     * 1) length is the same or diff is 1
     * 2) 80% of letters appears in both words
     *
     * @return
     */
    public static boolean wordEqual(String word1, String word2) {
        if (Math.abs(word1.length() - word2.length()) > 1) {
            return false;
        }

        int percentLetterRatio = (int) (word1.length() * 0.8);
        int matchedLetters = 0;

        for (char c : word1.toCharArray()) {
            if (word2.indexOf(c) > 0) {
                matchedLetters++;
            }
        }

        if (matchedLetters >= percentLetterRatio) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Split string into parts (delimiter is non numbers) and sum
     *
     * @param s example valie '1-2(7-11,8-11,11-4,9-8)'
     * @return
     */
    public static Integer sumOfNumbersInString(String s) {
        String[] arr = s.split("\\D+");

        int sum = Arrays.asList(arr)
                .stream().mapToInt(str -> {
                    int result = 0;
                    try {
                        result = Integer.parseInt(str);
                    } catch (Exception e) {

                    }
                    return result;
                }).sum();

        return sum;
    }
}
