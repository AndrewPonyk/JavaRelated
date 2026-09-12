package com.sortingsearching.searching;

import java.util.ArrayList;
import java.util.List;

public final class BinarySearchTree {
    private Node root;

    public void insert(int value) {
        root = insert(root, value);
    }

    public boolean contains(int value) {
        Node current = root;
        while (current != null) {
            if (value == current.value) {
                return true;
            }
            current = value < current.value ? current.left : current.right;
        }
        return false;
    }

    public List<Integer> inorder() {
        List<Integer> result = new ArrayList<>();
        inorder(root, result);
        return result;
    }

    private Node insert(Node node, int value) {
        if (node == null) {
            return new Node(value);
        }
        if (value < node.value) {
            node.left = insert(node.left, value);
        } else if (value > node.value) {
            node.right = insert(node.right, value);
        }
        return node;
    }

    private void inorder(Node node, List<Integer> result) {
        if (node == null) {
            return;
        }
        inorder(node.left, result);
        result.add(node.value);
        inorder(node.right, result);
    }

    private static final class Node {
        private final int value;
        private Node left;
        private Node right;

        private Node(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 99, 1};
        int target = 19;
        BinarySearchTree tree = new BinarySearchTree();
        for (int value : sample) {
            tree.insert(value);
        }

        System.out.println("Binary Search Tree");
        System.out.println("input  : [42, 7, 19, 3, 99, 1]");
        System.out.println("inorder: " + tree.inorder());
        System.out.println("target : " + target);
        System.out.println("found  : " + tree.contains(target));
    }
}
