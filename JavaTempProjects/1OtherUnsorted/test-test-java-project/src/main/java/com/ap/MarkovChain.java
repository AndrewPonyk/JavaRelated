package com.ap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class MarkovChain {
    private static Random r = new Random();


    public static void main(String[] args) throws IOException {
        //DWDDWDDWLLDLLDWDWW - LLLLLDDWWWWDWWLWDLDW
        System.out.println("DWDDWDDWLLDLLDWDWW");
        System.out.println(checkLast5ResultsInString("DWDDWDDWLLDLLDWDWW"));
        System.out.println("----------------------");

        System.out.println(markov(
                new String(Files.readAllBytes(Paths.get("/home/andrii/git/JavaRelated/JavaTempProjects/1OtherUnsorted/test-test-java-project/src/main/resources/cyberfootballchain.txt"))).trim(),
                5, 7));

        //alice oz text generation
//        System.out.println(markov(
//                "/home/andrii/git/JavaRelated/JavaTempProjects/1OtherUnsorted/test-test-java-project/src/main/resources/alice_oz.txt",
//                3, 10));
    }


    public static String markov(String filePath, int keySize, int outputSize, String firstWord) throws IOException {
        String result = "";

        //try 100 times, to find text which starts with specified word
        for (int i = 0; i < 100; i++) {
            result = markov(filePath, keySize, outputSize);
            if (result.trim().startsWith(firstWord)) {
                break;
            }
        }

        return result;
    }

    ////DWDDWDDWLLDLLDWDWW - LLLLLDDWWWWDWWLWDLDW
    public static String checkLast5ResultsInString(String results) {
        if (results == null || results.length() < 9) {
            return results;
        }

        try {
            String result = results.substring(0, results.length() - 5);

            for (int i = results.length() - 5; i < results.length(); i++) {
                String markov = markov(results.substring(0, i), 3, 4, results.charAt(i - 1) + "").trim();
                String prediction = markov.replaceAll(" ", "").charAt(1) + "";

                String wasPredictionCorrect = prediction.equals(results.charAt(i) + "") ||
                        prediction.equals("W") && "D".equals(results.charAt(i) + "") ||
                        prediction.equals("D") && "W".equals(results.charAt(i) + "") ? "+" : "-";
                result += "[" + results.charAt(i) + prediction + wasPredictionCorrect + "]";
            }
            String futurePrediction =
                    markov(results, 3, 4, results.charAt(results.length() - 1) + "")
                            .replaceAll(" ", "").charAt(1) + "";
            result = result + "(" + futurePrediction + ")";
            return result;
        } catch (Exception e) {
        }
        return results;
    }

    public static String markov(String text, int keySize, int outputSize) throws IOException {
        if (keySize < 1) throw new IllegalArgumentException("Key size can't be less than 1");
        //Path path = Paths.get(filePath);
        //String text = new String(Files.readAllBytes(path)).trim();

        String splitRegex = text.contains(" ") ? " " : "";
        String[] words = text.split(splitRegex);
        if (outputSize < keySize || outputSize >= words.length) {
            throw new IllegalArgumentException("Output size is out of range");
        }
        Map<String, List<String>> dict = new HashMap<>();

        for (int i = 0; i < (words.length - keySize); ++i) {
            StringBuilder key = new StringBuilder(words[i]);
            for (int j = i + 1; j < i + keySize; ++j) {
                key.append(' ').append(words[j]);
            }
            String value = (i + keySize < words.length) ? words[i + keySize] : "";
            if (!dict.containsKey(key.toString())) {
                ArrayList<String> list = new ArrayList<>();
                list.add(value);
                dict.put(key.toString(), list);
            } else {
                dict.get(key.toString()).add(value);
            }
        }

        int n = 0;
        int rn = r.nextInt(dict.size());
        String prefix = (String) dict.keySet().toArray()[rn];
        List<String> output = new ArrayList<>(Arrays.asList(prefix.split(" ")));

        while (true) {
            List<String> suffix = dict.get(prefix);
            if (suffix.size() == 1) {
                if (Objects.equals(suffix.get(0), "")) return output.stream().reduce("", (a, b) -> a + " " + b);
                output.add(suffix.get(0));
            } else {
                rn = r.nextInt(suffix.size());
                output.add(suffix.get(rn));
            }
            if (output.size() >= outputSize)
                return output.stream().limit(outputSize).reduce("", (a, b) -> a + " " + b);
            n++;
            prefix = output.stream().skip(n).limit(keySize).reduce("", (a, b) -> a + " " + b).trim();
        }
    }


}
