package com.example.greedy.algorithms;

import com.example.greedy.model.Item;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class FractionalKnapsack {
    private FractionalKnapsack() {
    }

    public static final class Take {
        private final String name;
        private final double fraction;
        private final double value;

        public Take(String name, double fraction, double value) {
            this.name = name;
            this.fraction = fraction;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Take{name='" + name + "', fraction=" + fraction + ", value=" + value + "}";
        }
    }

    public static final class Result {
        private final double totalValue;
        private final List<Take> taken;

        public Result(double totalValue, List<Take> taken) {
            this.totalValue = totalValue;
            this.taken = List.copyOf(taken);
        }

        public double totalValue() {
            return totalValue;
        }

        @Override
        public String toString() {
            return "Result{totalValue=" + totalValue + ", taken=" + taken + "}";
        }
    }

    public static Result solve(List<Item> items, double capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }

        double remaining = capacity;
        double totalValue = 0;
        List<Take> taken = new ArrayList<>();

        List<Item> sorted = items.stream()
                .sorted(Comparator.comparingDouble(Item::density).reversed())
                .collect(Collectors.toList());

        for (Item item : sorted) {
            if (remaining <= 0) {
                break;
            }
            double amount = Math.min(item.weight(), remaining);
            double fraction = amount / item.weight();
            double value = item.value() * fraction;
            taken.add(new Take(item.name(), round(fraction), round(value)));
            totalValue += value;
            remaining -= amount;
        }

        return new Result(round(totalValue), taken);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
