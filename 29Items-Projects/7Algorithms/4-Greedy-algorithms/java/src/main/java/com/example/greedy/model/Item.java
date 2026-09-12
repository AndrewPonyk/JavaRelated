package com.example.greedy.model;

public final class Item {
    private final String name;
    private final double value;
    private final double weight;

    public Item(String name, double value, double weight) {
        this.name = name;
        this.value = value;
        this.weight = weight;
    }

    public String name() {
        return name;
    }

    public double value() {
        return value;
    }

    public double weight() {
        return weight;
    }

    public double density() {
        if (weight <= 0) {
            throw new IllegalArgumentException(name + " must have positive weight");
        }
        return value / weight;
    }

    @Override
    public String toString() {
        return "Item{name='" + name + "', value=" + value + ", weight=" + weight + "}";
    }
}
