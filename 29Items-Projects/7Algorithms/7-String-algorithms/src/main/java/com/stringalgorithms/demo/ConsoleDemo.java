package com.stringalgorithms.demo;

import com.stringalgorithms.algorithms.AhoCorasick;
import com.stringalgorithms.algorithms.KmpMatcher;
import com.stringalgorithms.algorithms.LongestCommonSubsequence;
import com.stringalgorithms.algorithms.LongestCommonSubstring;
import com.stringalgorithms.algorithms.Manacher;
import com.stringalgorithms.algorithms.RabinKarp;
import com.stringalgorithms.algorithms.SuffixArray;
import com.stringalgorithms.algorithms.SuffixTree;
import com.stringalgorithms.algorithms.Trie;
import com.stringalgorithms.model.MatchResult;

import java.util.List;
import java.util.Map;

public final class ConsoleDemo {
    private static final String DEFAULT_TEXT = "abracadabra pattern matching abracadabra";
    private static final String DEFAULT_PATTERN = "abra";
    private static final String DEFAULT_PATTERNS = "abra,cad,match";
    private static final String DEFAULT_PALINDROME_TEXT = "forgeeksskeegfor";
    private static final String DEFAULT_COMPARISON_TEXT = "cadabra matching";

    private ConsoleDemo() {
    }

    public static void run(String[] args) {
        DemoConfig config = DemoConfig.from(args, System.getenv());

        printHeader("Java String Algorithm Demo");
        System.out.printf("Text: %s%n", config.text);
        System.out.printf("Pattern: %s%n", config.pattern);
        System.out.printf("Patterns: %s%n", config.patterns);
        System.out.printf("Comparison text: %s%n", config.comparisonText);
        printMatches("KMP", config.pattern, KmpMatcher.search(config.text, config.pattern));
        printMatches("Rabin-Karp", config.pattern, RabinKarp.search(config.text, config.pattern));

        AhoCorasick ahoCorasick = new AhoCorasick(config.patterns);
        System.out.println("Aho-Corasick matches:");
        for (MatchResult result : ahoCorasick.search(config.text)) {
            System.out.printf("  pattern=%s index=%d%n", result.getPattern(), result.getStartIndex());
        }

        SuffixArray suffixArray = new SuffixArray(config.text);
        printMatches("Suffix Array", config.pattern, suffixArray.search(config.pattern));

        SuffixTree suffixTree = new SuffixTree(config.text);
        System.out.printf("Suffix Tree contains '%s': %s%n", config.pattern, suffixTree.contains(config.pattern));
        System.out.printf("Suffix Tree edge count: %d%n", suffixTree.edgeCount());

        System.out.printf("Manacher longest palindrome in '%s': %s%n",
                config.palindromeText,
                Manacher.longestPalindrome(config.palindromeText));

        Trie trie = new Trie();
        for (String value : config.patterns) {
            trie.insert(value);
        }
        System.out.printf("Trie contains '%s': %s%n", config.patterns.get(0), trie.contains(config.patterns.get(0)));
        System.out.printf("Trie startsWith '%s': %s%n", prefixOf(config.pattern), trie.startsWith(prefixOf(config.pattern)));

        String lcs = LongestCommonSubsequence.find(config.text, config.comparisonText);
        String longestCommonSubstring = LongestCommonSubstring.find(config.text, config.comparisonText);
        System.out.printf("Longest Common Subsequence with comparison text: '%s' (length=%d)%n",
                lcs,
                lcs.length());
        System.out.printf("Longest Common Substring with comparison text: '%s' (length=%d)%n",
                longestCommonSubstring,
                longestCommonSubstring.length());

        if (config.benchmark) {
            printBenchmark(config.text, config.pattern, config.patterns, config.comparisonText);
        }
    }

    private static void printHeader(String title) {
        System.out.println("=".repeat(title.length()));
        System.out.println(title);
        System.out.println("=".repeat(title.length()));
    }

    private static void printMatches(String algorithm, String pattern, List<Integer> indexes) {
        System.out.printf("%s matches for '%s': %s%n", algorithm, pattern, indexes);
    }

    private static String prefixOf(String value) {
        return value.substring(0, Math.min(3, value.length()));
    }

    private static void printBenchmark(String text, String pattern, List<String> patterns, String comparisonText) {
        System.out.println("Benchmark snapshot:");
        measure("KMP", () -> KmpMatcher.search(text, pattern));
        measure("Rabin-Karp", () -> RabinKarp.search(text, pattern));
        measure("Aho-Corasick", () -> new AhoCorasick(patterns).search(text));
        measure("Suffix Array build+search", () -> new SuffixArray(text).search(pattern));
        measure("Suffix Tree build+contains", () -> new SuffixTree(text).contains(pattern));
        measure("Manacher", () -> Manacher.longestPalindrome(text));
        measure("LCS", () -> LongestCommonSubsequence.find(text, comparisonText));
        measure("Longest Common Substring", () -> LongestCommonSubstring.find(text, comparisonText));
    }

    private static void measure(String label, Runnable action) {
        long start = System.nanoTime();
        action.run();
        long elapsedMicros = (System.nanoTime() - start) / 1_000;
        System.out.printf("  %-24s %d microseconds%n", label, elapsedMicros);
    }

    private static final class DemoConfig {
        private final String text;
        private final String pattern;
        private final List<String> patterns;
        private final String palindromeText;
        private final String comparisonText;
        private final boolean benchmark;

        private DemoConfig(
                String text,
                String pattern,
                List<String> patterns,
                String palindromeText,
                String comparisonText,
                boolean benchmark) {
            this.text = requireNonBlank(text, "text");
            this.pattern = requireNonBlank(pattern, "pattern");
            this.patterns = parsePatterns(String.join(",", patterns));
            this.palindromeText = requireNonBlank(palindromeText, "palindrome text");
            this.comparisonText = requireNonBlank(comparisonText, "comparison text");
            this.benchmark = benchmark;
        }

        private static DemoConfig from(String[] args, Map<String, String> env) {
            String text = env.getOrDefault("STRING_ALGORITHMS_SAMPLE_TEXT", DEFAULT_TEXT);
            String pattern = env.getOrDefault("STRING_ALGORITHMS_PATTERN", DEFAULT_PATTERN);
            String patternsValue = env.getOrDefault("STRING_ALGORITHMS_PATTERNS", DEFAULT_PATTERNS);
            String palindromeText = env.getOrDefault("STRING_ALGORITHMS_PALINDROME_TEXT", DEFAULT_PALINDROME_TEXT);
            String comparisonText = env.getOrDefault("STRING_ALGORITHMS_COMPARISON_TEXT", DEFAULT_COMPARISON_TEXT);
            boolean benchmark = Boolean.parseBoolean(env.getOrDefault("STRING_ALGORITHMS_BENCHMARK", "false"));

            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if ("--benchmark".equals(arg)) {
                    benchmark = true;
                } else if (arg.startsWith("--text=")) {
                    text = arg.substring("--text=".length());
                } else if ("--text".equals(arg)) {
                    text = nextValue(args, ++index, "--text");
                } else if (arg.startsWith("--pattern=")) {
                    pattern = arg.substring("--pattern=".length());
                } else if ("--pattern".equals(arg)) {
                    pattern = nextValue(args, ++index, "--pattern");
                } else if (arg.startsWith("--patterns=")) {
                    patternsValue = arg.substring("--patterns=".length());
                } else if ("--patterns".equals(arg)) {
                    patternsValue = nextValue(args, ++index, "--patterns");
                } else if (arg.startsWith("--palindrome-text=")) {
                    palindromeText = arg.substring("--palindrome-text=".length());
                } else if ("--palindrome-text".equals(arg)) {
                    palindromeText = nextValue(args, ++index, "--palindrome-text");
                } else if (arg.startsWith("--comparison-text=")) {
                    comparisonText = arg.substring("--comparison-text=".length());
                } else if ("--comparison-text".equals(arg)) {
                    comparisonText = nextValue(args, ++index, "--comparison-text");
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new DemoConfig(text, pattern, parsePatterns(patternsValue), palindromeText, comparisonText, benchmark);
        }

        private static String nextValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }

        private static List<String> parsePatterns(String patternsValue) {
            List<String> parsed = java.util.Arrays.stream(patternsValue.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("patterns must contain at least one value");
            }
            return parsed;
        }

        private static String requireNonBlank(String value, String name) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
