package com.example.greedy.model;

public final class Job {
    private final String name;
    private final int deadline;
    private final int profit;

    public Job(String name, int deadline, int profit) {
        this.name = name;
        this.deadline = deadline;
        this.profit = profit;
    }

    public String name() {
        return name;
    }

    public int deadline() {
        return deadline;
    }

    public int profit() {
        return profit;
    }

    @Override
    public String toString() {
        return "Job{name='" + name + "', deadline=" + deadline + ", profit=" + profit + "}";
    }
}
