package com.example.greedy.algorithms;

import com.example.greedy.model.Activity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class ActivitySelection {
    private ActivitySelection() {
    }

    public static List<Activity> select(List<Activity> activities) {
        for (Activity activity : activities) {
            if (activity.finish() < activity.start()) {
                throw new IllegalArgumentException(activity.name() + " finishes before it starts");
            }
        }

        List<Activity> sorted = activities.stream()
                .sorted(Comparator.comparingInt(Activity::finish)
                        .thenComparingInt(Activity::start)
                        .thenComparing(Activity::name))
                .collect(Collectors.toList());

        List<Activity> selected = new ArrayList<>();
        int currentFinish = -1;
        for (Activity activity : sorted) {
            if (activity.start() >= currentFinish) {
                selected.add(activity);
                currentFinish = activity.finish();
            }
        }
        return selected;
    }
}
