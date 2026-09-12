package com.stringalgorithms.algorithms;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Trie {
    private final Node root = new Node();

    public void insert(String word) {
        Objects.requireNonNull(word, "word must not be null");
        Node current = root;
        for (char ch : word.toCharArray()) {
            current = current.children.computeIfAbsent(ch, ignored -> new Node());
        }
        current.word = true;
    }

    public boolean contains(String word) {
        Node node = findNode(word);
        return node != null && node.word;
    }

    public boolean startsWith(String prefix) {
        return findNode(prefix) != null;
    }

    private Node findNode(String value) {
        Objects.requireNonNull(value, "value must not be null");
        Node current = root;
        for (char ch : value.toCharArray()) {
            current = current.children.get(ch);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private boolean word;
    }
}
