package com.example.greedy.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.greedy.model.Activity;
import com.example.greedy.model.Edge;
import com.example.greedy.model.Item;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class GreedyAlgorithmsTest {
    @Test
    void activitySelectionPrefersEarliestFinish() {
        List<Activity> selected = ActivitySelection.select(List.of(
                new Activity("A1", 1, 4),
                new Activity("A2", 3, 5),
                new Activity("A3", 5, 7),
                new Activity("A4", 6, 9)));

        assertEquals(
                List.of("A1", "A3"),
                selected.stream().map(Activity::name).collect(Collectors.toList()));
    }

    @Test
    void fractionalKnapsackAllowsPartialItem() {
        FractionalKnapsack.Result result = FractionalKnapsack.solve(
                List.of(
                        new Item("Gold", 60, 10),
                        new Item("Silver", 100, 20),
                        new Item("Bronze", 120, 30)),
                50);

        assertEquals(240.0, result.totalValue());
    }

    @Test
    void kruskalMstTotalWeight() {
        KruskalMst.Result result = KruskalMst.solve(
                4,
                List.of(
                        new Edge(0, 1, 10),
                        new Edge(0, 2, 6),
                        new Edge(0, 3, 5),
                        new Edge(1, 3, 15),
                        new Edge(2, 3, 4)));

        assertEquals(19, result.totalWeight());
    }
}
