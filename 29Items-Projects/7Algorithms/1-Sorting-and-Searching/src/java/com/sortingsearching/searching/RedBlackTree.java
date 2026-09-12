package com.sortingsearching.searching;

import java.util.ArrayList;
import java.util.List;

public final class RedBlackTree {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private final Node nil = new Node(0, BLACK);
    private Node root = nil;

    public void insert(int value) {
        Node node = new Node(value, RED);
        node.left = nil;
        node.right = nil;

        Node parent = nil;
        Node current = root;
        while (current != nil) {
            parent = current;
            current = node.value < current.value ? current.left : current.right;
        }

        node.parent = parent;
        if (parent == nil) {
            root = node;
        } else if (node.value < parent.value) {
            parent.left = node;
        } else {
            parent.right = node;
        }

        fixInsert(node);
    }

    public boolean contains(int value) {
        Node current = root;
        while (current != nil) {
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

    private void fixInsert(Node node) {
        while (node.parent != null && node.parent.color == RED) {
            Node grandparent = node.parent.parent;
            if (grandparent == null) {
                break;
            }
            if (node.parent == grandparent.left) {
                Node uncle = grandparent.right;
                if (uncle.color == RED) {
                    node.parent.color = BLACK;
                    uncle.color = BLACK;
                    grandparent.color = RED;
                    node = grandparent;
                } else {
                    if (node == node.parent.right) {
                        node = node.parent;
                        leftRotate(node);
                    }
                    node.parent.color = BLACK;
                    grandparent.color = RED;
                    rightRotate(grandparent);
                }
            } else {
                Node uncle = grandparent.left;
                if (uncle.color == RED) {
                    node.parent.color = BLACK;
                    uncle.color = BLACK;
                    grandparent.color = RED;
                    node = grandparent;
                } else {
                    if (node == node.parent.left) {
                        node = node.parent;
                        rightRotate(node);
                    }
                    node.parent.color = BLACK;
                    grandparent.color = RED;
                    leftRotate(grandparent);
                }
            }
        }
        root.color = BLACK;
    }

    private void leftRotate(Node node) {
        Node right = node.right;
        node.right = right.left;
        if (right.left != nil) {
            right.left.parent = node;
        }
        right.parent = node.parent;
        if (node.parent == nil) {
            root = right;
        } else if (node == node.parent.left) {
            node.parent.left = right;
        } else {
            node.parent.right = right;
        }
        right.left = node;
        node.parent = right;
    }

    private void rightRotate(Node node) {
        Node left = node.left;
        node.left = left.right;
        if (left.right != nil) {
            left.right.parent = node;
        }
        left.parent = node.parent;
        if (node.parent == nil) {
            root = left;
        } else if (node == node.parent.right) {
            node.parent.right = left;
        } else {
            node.parent.left = left;
        }
        left.right = node;
        node.parent = left;
    }

    private void inorder(Node node, List<Integer> result) {
        if (node == nil) {
            return;
        }
        inorder(node.left, result);
        result.add(node.value);
        inorder(node.right, result);
    }

    private static final class Node {
        private final int value;
        private boolean color;
        private Node left;
        private Node right;
        private Node parent;

        private Node(int value, boolean color) {
            this.value = value;
            this.color = color;
        }
    }

    public static void main(String[] args) {
        int[] sample = {42, 7, 19, 3, 99, 1};
        int target = 19;
        RedBlackTree tree = new RedBlackTree();
        for (int value : sample) {
            tree.insert(value);
        }

        System.out.println("Red-Black Tree");
        System.out.println("input  : [42, 7, 19, 3, 99, 1]");
        System.out.println("inorder: " + tree.inorder());
        System.out.println("target : " + target);
        System.out.println("found  : " + tree.contains(target));
    }
}
