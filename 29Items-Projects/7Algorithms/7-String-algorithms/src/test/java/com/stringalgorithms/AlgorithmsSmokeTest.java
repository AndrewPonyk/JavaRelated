package com.stringalgorithms;

import com.stringalgorithms.algorithms.AhoCorasick;
import com.stringalgorithms.algorithms.KmpMatcher;
import com.stringalgorithms.algorithms.LongestCommonSubsequence;
import com.stringalgorithms.algorithms.LongestCommonSubstring;
import com.stringalgorithms.algorithms.Manacher;
import com.stringalgorithms.algorithms.RabinKarp;
import com.stringalgorithms.algorithms.SuffixArray;
import com.stringalgorithms.algorithms.SuffixTree;
import com.stringalgorithms.algorithms.Trie;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmsSmokeTest {
    @Test
    void findsOverlappingMatches() {
        assertEquals(List.of(0, 1, 2), KmpMatcher.search("aaaa", "aa"));
        assertEquals(List.of(0, 1, 2), RabinKarp.search("aaaa", "aa"));
    }

    @Test
    void returnsNoMatchesWhenPatternIsMissing() {
        assertEquals(List.of(), KmpMatcher.search("abcdef", "xyz"));
        assertEquals(List.of(), RabinKarp.search("abcdef", "xyz"));
        assertEquals(List.of(), new SuffixArray("abcdef").search("xyz"));
        assertFalse(new SuffixTree("abcdef").contains("xyz"));
    }

    @Test
    void validatesEmptyPatterns() {
        assertThrows(IllegalArgumentException.class, () -> KmpMatcher.search("abc", ""));
        assertThrows(IllegalArgumentException.class, () -> RabinKarp.search("abc", ""));
        assertThrows(IllegalArgumentException.class, () -> new SuffixArray("abc").search(""));
        assertThrows(IllegalArgumentException.class, () -> new AhoCorasick(List.of("")));
    }

    @Test
    void supportsMultiplePatternMatching() {
        AhoCorasick matcher = new AhoCorasick(List.of("he", "she", "hers"));
        assertEquals(3, matcher.search("ushers").size());
    }

    @Test
    void supportsSuffixBasedSearches() {
        SuffixArray suffixArray = new SuffixArray("banana");
        assertEquals(List.of(1, 3), suffixArray.search("ana"));
        SuffixTree suffixTree = new SuffixTree("banana");
        assertTrue(suffixTree.contains("nan"));
        assertTrue(suffixTree.edgeCount() > 0);
    }

    @Test
    void supportsPalindromeAndTrieLookups() {
        assertEquals("geeksskeeg", Manacher.longestPalindrome("forgeeksskeegfor"));

        Trie trie = new Trie();
        trie.insert("pattern");
        assertTrue(trie.contains("pattern"));
        assertTrue(trie.startsWith("pat"));
    }

    @Test
    void supportsSequenceComparisonAlgorithms() {
        assertEquals("GTAB", LongestCommonSubsequence.find("AGGTAB", "GXTXAYB"));
        assertEquals(4, LongestCommonSubsequence.length("AGGTAB", "GXTXAYB"));
        assertEquals("BABC", LongestCommonSubstring.find("ABABC", "BABCA"));
        assertEquals(4, LongestCommonSubstring.length("ABABC", "BABCA"));
        assertEquals("", LongestCommonSubsequence.find("", "abc"));
        assertEquals("", LongestCommonSubstring.find("abc", "xyz"));
    }

    @Test
    void handlesEmptyTextAndUnicodeSamples() {
        assertEquals(List.of(), KmpMatcher.search("", "abc"));
        assertEquals(List.of(), RabinKarp.search("", "abc"));
        assertEquals(List.of(0, 5), KmpMatcher.search("caf\u00e9 caf\u00e9", "caf\u00e9"));
        assertEquals(List.of(0, 5), RabinKarp.search("caf\u00e9 caf\u00e9", "caf\u00e9"));
        assertEquals("anana", Manacher.longestPalindrome("bananas"));
    }

    @Test
    void consoleDemoAcceptsCustomArguments() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer));
            com.stringalgorithms.demo.ConsoleDemo.run(new String[]{
                    "--text", "mississippi",
                    "--pattern", "issi",
                    "--patterns", "is,ssi,miss",
                    "--palindrome-text", "abacdfgdcaba",
                    "--comparison-text", "missouri"
            });
        } finally {
            System.setOut(originalOut);
        }

        String output = buffer.toString();
        assertTrue(output.contains("KMP matches for 'issi': [1, 4]"));
        assertTrue(output.contains("Aho-Corasick matches:"));
        assertTrue(output.contains("Manacher longest palindrome"));
        assertTrue(output.contains("Longest Common Subsequence"));
        assertTrue(output.contains("Longest Common Substring"));
    }

    @Test
    void mainReturnsErrorCodeForInvalidArguments() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(buffer));
            assertEquals(2, Main.run(new String[]{"--pattern", ""}));
        } finally {
            System.setErr(originalErr);
        }
        assertTrue(buffer.toString().contains("pattern must not be blank"));
    }
}
