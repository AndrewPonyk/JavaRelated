package com.example.dp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class DynamicProgrammingSmokeTest {
    @Test
    void knapsackVariantsMatch() {
        int[] weights = {2, 3, 4, 5};
        int[] values = {3, 4, 5, 8};
        assertEquals(12, Knapsack.memoized(weights, values, 8).value());
        assertEquals(12, Knapsack.tabulated(weights, values, 8).value());
        assertEquals(12, Knapsack.spaceOptimized(weights, values, 8).value());
    }

    @Test
    void lcsReconstructsSequence() {
        Result result = LongestCommonSubsequence.tabulated("AGGTAB", "GXTXAYB");
        assertEquals(4, result.value());
        assertEquals("GTAB", result.details().get("sequence"));
    }

    @Test
    void editDistanceWorks() {
        assertEquals(3, EditDistance.spaceOptimized("kitten", "sitting").value());
    }

    @Test
    void tspFindsBestTourCost() {
        int[][] dist = {
                {0, 10, 15, 20},
                {10, 0, 35, 25},
                {15, 35, 0, 30},
                {20, 25, 30, 0}
        };
        assertEquals(80, TravelingSalesman.tabulated(dist).value());
    }

    @Test
    void addedLinearDpAlgorithmsWork() {
        assertEquals(55, Fibonacci.spaceOptimized(10).value());
        assertEquals(8, ClimbingStairs.tabulated(5).value());
        assertEquals(12, HouseRobber.tabulated(new int[] {2, 7, 9, 3, 1}).value());
    }

    @Test
    void addedGridDpAlgorithmsWork() {
        assertEquals(28, UniquePaths.spaceOptimized(3, 7).value());
        int[][] grid = {
                {1, 3, 1},
                {1, 5, 1},
                {4, 2, 1}
        };
        assertEquals(7, MinimumPathSum.tabulated(grid).value());
    }

    @Test
    void addedSubsetDpAlgorithmsWork() {
        assertTrue((Boolean) SubsetSum.tabulated(new int[] {3, 34, 4, 12, 5, 2}, 9).value());
        assertTrue((Boolean) EqualPartition.spaceOptimized(new int[] {1, 5, 11, 5}).value());
    }

    @Test
    void addedStringDpAlgorithmsWork() {
        assertEquals("BABC", LongestCommonSubstring.tabulated("ABABC", "BABCA").details().get("substring"));
        assertEquals(4, LongestPalindromicSubsequence.spaceOptimized("bbbab").value());
        assertTrue((Boolean) WordBreak.tabulated("leetcode", Set.of("leet", "code")).value());
    }
}
