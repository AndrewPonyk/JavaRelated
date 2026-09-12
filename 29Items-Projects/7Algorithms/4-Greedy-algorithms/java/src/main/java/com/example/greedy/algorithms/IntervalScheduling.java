package com.example.greedy.algorithms;

import com.example.greedy.model.Interval;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class IntervalScheduling {
    private IntervalScheduling() {
    }

    public static List<Interval> schedule(List<Interval> intervals) {
        for (Interval interval : intervals) {
            if (interval.finish() < interval.start()) {
                throw new IllegalArgumentException(interval.name() + " finishes before it starts");
            }
        }

        List<Interval> selected = new ArrayList<>();
        int lastFinish = -1;
        for (Interval interval : intervals.stream()
                .sorted(Comparator.comparingInt(Interval::finish)
                        .thenComparingInt(Interval::start)
                        .thenComparing(Interval::name))
                .collect(Collectors.toList())) {
            if (interval.start() >= lastFinish) {
                selected.add(interval);
                lastFinish = interval.finish();
            }
        }
        return selected;
    }
}
