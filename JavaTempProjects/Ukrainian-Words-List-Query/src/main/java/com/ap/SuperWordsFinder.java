package com.ap;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SuperWordsFinder {
    public static void main(String[] args) {
        System.out.println("FIND word");
        File file = new File("D:\\mygit\\dict_uk\\data\\freq\\uk_wordlist.xml");
        

        try {
            List<String> contents = FileUtils.readLines(file, "UTF-8");
            List<String> words = contents.stream().map(s -> {
                if (!s.contains("flags")) {
                    return null;
                }
                return s.substring(s.indexOf("\">") + 2, s.indexOf("</"));
            }).filter(Objects::nonNull)
                    .collect(Collectors.toList());
            // Iterate the result to print each line of the file.
            List<String> superWOrds = findSuperWords(5, words);
            System.out.println("superWOrds = " + superWOrds);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static List<String> findSuperWords(int length, List<String> words) {
        String firstWord = "ааааа";
        List<String> superWords = new ArrayList<>();
        final Optional<String> any = words.stream().filter(w -> isUniqueChars(w) && w.length() == length).findFirst();
        if (any.isPresent()) {
            firstWord = any.get();
            superWords.add(firstWord);
        }

        for (int i = 0; i < 100; i++) {
            String concat = String.join("", superWords);
            Optional<String> next = words.stream().filter(s-> !superWords.contains(s)
                    && notSontains(s, concat)
                    && notSontains(s, "'")
                    && s.length() == length).findFirst();
            if(next.isPresent())
                superWords.add(next.get());
        }
        return superWords;

    }

    private static boolean isUniqueChars(String copy) {
        if (copy.length() <= 1) return true;
        Set<Character> set = copy.chars()
                .mapToObj(e -> (char) e).collect(Collectors.toSet());
        if (set.size() < copy.length()) {
            return false;
        }
        return true;
    }

    public static boolean notSontains(String word, String s){
        for (int i = 0; i < s.length(); i++) {
            if(word.contains(s.charAt(i)+"")){
                return false;
            }
        }
        return true;
    }
}