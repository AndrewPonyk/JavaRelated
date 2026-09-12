package com.stringalgorithms.algorithms;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SuffixTree {
    private final Node root = new Node();

    public SuffixTree(String text) {
        Objects.requireNonNull(text, "text must not be null");
        for (int start = 0; start < text.length(); start++) {
            insert(text.substring(start));
        }
    }

    public boolean contains(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (pattern.isEmpty()) {
            return true;
        }

        Node current = root;
        int patternIndex = 0;

        while (patternIndex < pattern.length()) {
            Edge edge = current.children.get(pattern.charAt(patternIndex));
            if (edge == null) {
                return false;
            }

            int edgeIndex = 0;
            while (edgeIndex < edge.label.length() && patternIndex < pattern.length()) {
                if (edge.label.charAt(edgeIndex) != pattern.charAt(patternIndex)) {
                    return false;
                }
                edgeIndex++;
                patternIndex++;
            }

            if (patternIndex == pattern.length()) {
                return true;
            }

            current = edge.target;
        }
        return true;
    }

    public int edgeCount() {
        return countEdges(root);
    }

    private void insert(String suffix) {
        Node current = root;
        String remaining = suffix;

        while (!remaining.isEmpty()) {
            Edge edge = current.children.get(remaining.charAt(0));
            if (edge == null) {
                Node leaf = new Node();
                leaf.terminal = true;
                current.children.put(remaining.charAt(0), new Edge(remaining, leaf));
                return;
            }

            int commonLength = commonPrefixLength(remaining, edge.label);
            if (commonLength == edge.label.length()) {
                current = edge.target;
                remaining = remaining.substring(commonLength);
                continue;
            }

            splitEdge(current, edge, commonLength, remaining);
            return;
        }

        current.terminal = true;
    }

    private void splitEdge(Node parent, Edge edge, int commonLength, String remaining) {
        String commonPrefix = edge.label.substring(0, commonLength);
        String oldRemainder = edge.label.substring(commonLength);
        Node intermediate = new Node();
        intermediate.children.put(oldRemainder.charAt(0), new Edge(oldRemainder, edge.target));

        edge.label = commonPrefix;
        edge.target = intermediate;
        parent.children.put(commonPrefix.charAt(0), edge);

        String newRemainder = remaining.substring(commonLength);
        if (newRemainder.isEmpty()) {
            intermediate.terminal = true;
        } else {
            Node leaf = new Node();
            leaf.terminal = true;
            intermediate.children.put(newRemainder.charAt(0), new Edge(newRemainder, leaf));
        }
    }

    private static int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private static int countEdges(Node node) {
        int total = node.children.size();
        for (Edge edge : node.children.values()) {
            total += countEdges(edge.target);
        }
        return total;
    }

    private static final class Node {
        private final Map<Character, Edge> children = new HashMap<>();
        private boolean terminal;
    }

    private static final class Edge {
        private String label;
        private Node target;

        private Edge(String label, Node target) {
            this.label = label;
            this.target = target;
        }
    }
}
