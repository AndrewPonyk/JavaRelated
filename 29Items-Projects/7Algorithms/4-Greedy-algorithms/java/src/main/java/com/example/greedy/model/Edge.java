package com.example.greedy.model;

public final class Edge {
    private final int source;
    private final int target;
    private final int weight;

    public Edge(int source, int target, int weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
    }

    public int source() {
        return source;
    }

    public int target() {
        return target;
    }

    public int weight() {
        return weight;
    }

    @Override
    public String toString() {
        return "Edge{source=" + source + ", target=" + target + ", weight=" + weight + "}";
    }
}
