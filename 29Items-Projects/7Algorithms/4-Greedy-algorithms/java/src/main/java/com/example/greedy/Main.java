package com.example.greedy;

import com.example.greedy.algorithms.ActivitySelection;
import com.example.greedy.algorithms.FractionalKnapsack;
import com.example.greedy.algorithms.HuffmanCoding;
import com.example.greedy.algorithms.IntervalScheduling;
import com.example.greedy.algorithms.JobScheduling;
import com.example.greedy.algorithms.KruskalMst;
import com.example.greedy.console.AlgorithmConsoleView;
import com.example.greedy.model.Activity;
import com.example.greedy.model.Edge;
import com.example.greedy.model.Interval;
import com.example.greedy.model.Item;
import com.example.greedy.model.Job;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        AlgorithmConsoleView view = new AlgorithmConsoleView();

        List<Activity> activities = List.of(
                new Activity("A1", 1, 4),
                new Activity("A2", 3, 5),
                new Activity("A3", 0, 6),
                new Activity("A4", 5, 7),
                new Activity("A5", 8, 9),
                new Activity("A6", 5, 9));
        view.runDemo(
                "Activity Selection",
                () -> ActivitySelection.select(activities),
                "choosing the earliest finishing compatible activity leaves maximum room for the rest.");

        List<Interval> intervals = List.of(
                new Interval("I1", 0, 3),
                new Interval("I2", 1, 4),
                new Interval("I3", 3, 5),
                new Interval("I4", 4, 7),
                new Interval("I5", 5, 9));
        view.runDemo(
                "Interval Scheduling",
                () -> IntervalScheduling.schedule(intervals),
                "the exchange argument replaces any first choice with the earliest finisher.");

        List<Item> items = List.of(
                new Item("Gold", 60, 10),
                new Item("Silver", 100, 20),
                new Item("Bronze", 120, 30));
        view.runDemo(
                "Fractional Knapsack",
                () -> FractionalKnapsack.solve(items, 50),
                "taking highest value density first is optimal because fractions can be exchanged locally.");

        List<Job> jobs = List.of(
                new Job("J1", 2, 100),
                new Job("J2", 1, 19),
                new Job("J3", 2, 27),
                new Job("J4", 1, 25),
                new Job("J5", 3, 15));
        view.runDemo(
                "Job Scheduling",
                () -> JobScheduling.schedule(jobs),
                "scheduling higher-profit jobs as late as possible preserves earlier slots for tighter jobs.");

        view.runDemo(
                "Huffman Coding",
                () -> HuffmanCoding.codes("greedy algorithms"),
                "merging the two least frequent symbols creates an optimal prefix-code subtree.");

        List<Edge> edges = List.of(
                new Edge(0, 1, 10),
                new Edge(0, 2, 6),
                new Edge(0, 3, 5),
                new Edge(1, 3, 15),
                new Edge(2, 3, 4));
        view.runDemo(
                "Minimum Spanning Tree",
                () -> KruskalMst.solve(4, edges),
                "the cut property allows choosing the lightest edge crossing any cut.");
    }
}
