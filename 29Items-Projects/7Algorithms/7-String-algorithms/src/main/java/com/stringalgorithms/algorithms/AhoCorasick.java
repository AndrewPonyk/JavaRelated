package com.stringalgorithms.algorithms;

import com.stringalgorithms.model.MatchResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

public final class AhoCorasick {
    private final Node root = new Node();
    private final List<String> patterns;

    public AhoCorasick(List<String> patterns) {
        Objects.requireNonNull(patterns, "patterns must not be null");
        if (patterns.isEmpty() || patterns.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("patterns must contain at least one non-empty value");
        }
        this.patterns = List.copyOf(patterns);
        buildTrie();
        buildFailureLinks();
    }

    public List<MatchResult> search(String text) {
        Objects.requireNonNull(text, "text must not be null");
        List<MatchResult> matches = new ArrayList<>();
        Node current = root;

        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            while (current != root && !current.children.containsKey(ch)) {
                current = current.failure;
            }
            current = current.children.getOrDefault(ch, root);

            for (int patternIndex : current.outputs) {
                String pattern = patterns.get(patternIndex);
                matches.add(new MatchResult("Aho-Corasick", pattern, index - pattern.length() + 1));
            }
        }

        return matches;
    }

    private void buildTrie() {
        for (int patternIndex = 0; patternIndex < patterns.size(); patternIndex++) {
            Node current = root;
            for (char ch : patterns.get(patternIndex).toCharArray()) {
                current = current.children.computeIfAbsent(ch, ignored -> new Node());
            }
            current.outputs.add(patternIndex);
        }
    }

    private void buildFailureLinks() {
        Queue<Node> queue = new ArrayDeque<>();
        root.failure = root;

        for (Node child : root.children.values()) {
            child.failure = root;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            Node current = queue.remove();
            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                Node child = entry.getValue();
                Node fallback = current.failure;

                while (fallback != root && !fallback.children.containsKey(ch)) {
                    fallback = fallback.failure;
                }

                child.failure = fallback.children.getOrDefault(ch, root);
                child.outputs.addAll(child.failure.outputs);
                queue.add(child);
            }
        }
    }

    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private final List<Integer> outputs = new ArrayList<>();
        private Node failure;
    }
}
