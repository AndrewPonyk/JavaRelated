package com.example.greedy.algorithms;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

public final class HuffmanCoding {
    private HuffmanCoding() {
    }

    public static Map<Character, String> codes(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text must not be empty");
        }

        Map<Character, Integer> frequencies = new HashMap<>();
        for (char symbol : text.toCharArray()) {
            frequencies.merge(symbol, 1, Integer::sum);
        }

        PriorityQueue<Node> queue = new PriorityQueue<>();
        int order = 0;
        for (Map.Entry<Character, Integer> entry : frequencies.entrySet()) {
            queue.add(new Node(entry.getValue(), order++, entry.getKey(), null, null));
        }

        if (queue.size() == 1) {
            Node only = queue.remove();
        return Map.of(only.symbol(), "0");
        }

        while (queue.size() > 1) {
            Node left = queue.remove();
            Node right = queue.remove();
            queue.add(new Node(left.frequency() + right.frequency(), order++, null, left, right));
        }

        Map<Character, String> result = new TreeMap<>();
        walk(queue.remove(), "", result);
        return result;
    }

    private static void walk(Node node, String prefix, Map<Character, String> result) {
        if (node.symbol() != null) {
            result.put(node.symbol(), prefix);
            return;
        }
        if (node.left() != null) {
            walk(node.left(), prefix + "0", result);
        }
        if (node.right() != null) {
            walk(node.right(), prefix + "1", result);
        }
    }

    private static final class Node implements Comparable<Node> {
        private final int frequency;
        private final int order;
        private final Character symbol;
        private final Node left;
        private final Node right;

        private Node(int frequency, int order, Character symbol, Node left, Node right) {
            this.frequency = frequency;
            this.order = order;
            this.symbol = symbol;
            this.left = left;
            this.right = right;
        }

        private int frequency() {
            return frequency;
        }

        private Character symbol() {
            return symbol;
        }

        private Node left() {
            return left;
        }

        private Node right() {
            return right;
        }

        @Override
        public int compareTo(Node other) {
            int byFrequency = Integer.compare(frequency, other.frequency);
            if (byFrequency != 0) {
                return byFrequency;
            }
            return Integer.compare(order, other.order);
        }
    }
}
