package com.example.greedy.model;

public final class Interval {
    private final String name;
    private final int start;
    private final int finish;

    public Interval(String name, int start, int finish) {
        this.name = name;
        this.start = start;
        this.finish = finish;
    }

    public String name() {
        return name;
    }

    public int start() {
        return start;
    }

    public int finish() {
        return finish;
    }

    @Override
    public String toString() {
        return "Interval{name='" + name + "', start=" + start + ", finish=" + finish + "}";
    }
}
