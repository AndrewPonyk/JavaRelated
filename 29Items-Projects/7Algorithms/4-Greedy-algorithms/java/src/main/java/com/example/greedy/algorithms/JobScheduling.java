package com.example.greedy.algorithms;

import com.example.greedy.model.Job;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class JobScheduling {
    private JobScheduling() {
    }

    public static final class Result {
        private final List<Job> jobs;
        private final int totalProfit;

        public Result(List<Job> jobs, int totalProfit) {
            this.jobs = List.copyOf(jobs);
            this.totalProfit = totalProfit;
        }

        @Override
        public String toString() {
            return "Result{jobs=" + jobs + ", totalProfit=" + totalProfit + "}";
        }
    }

    public static Result schedule(List<Job> jobs) {
        int maxDeadline = 0;
        for (Job job : jobs) {
            if (job.deadline() <= 0) {
                throw new IllegalArgumentException("job deadlines must be positive");
            }
            maxDeadline = Math.max(maxDeadline, job.deadline());
        }

        Job[] slots = new Job[maxDeadline];
        for (Job job : jobs.stream()
                .sorted(Comparator.comparingInt(Job::profit).reversed())
                .collect(Collectors.toList())) {
            for (int slot = Math.min(job.deadline(), maxDeadline) - 1; slot >= 0; slot--) {
                if (slots[slot] == null) {
                    slots[slot] = job;
                    break;
                }
            }
        }

        List<Job> selected = new ArrayList<>();
        int totalProfit = 0;
        for (Job job : slots) {
            if (job != null) {
                selected.add(job);
                totalProfit += job.profit();
            }
        }
        return new Result(selected, totalProfit);
    }
}
