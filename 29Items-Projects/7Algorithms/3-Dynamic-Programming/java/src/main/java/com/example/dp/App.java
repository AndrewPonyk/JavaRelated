package com.example.dp;

import java.util.List;
import java.util.Set;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        List<Result> results = List.of(
                Knapsack.memoized(new int[] {2, 3, 4, 5}, new int[] {3, 4, 5, 8}, 8),
                Knapsack.tabulated(new int[] {2, 3, 4, 5}, new int[] {3, 4, 5, 8}, 8),
                Knapsack.spaceOptimized(new int[] {2, 3, 4, 5}, new int[] {3, 4, 5, 8}, 8),
                LongestCommonSubsequence.memoized("AGGTAB", "GXTXAYB"),
                LongestCommonSubsequence.tabulated("AGGTAB", "GXTXAYB"),
                LongestCommonSubsequence.spaceOptimized("AGGTAB", "GXTXAYB"),
                LongestIncreasingSubsequence.memoized(new int[] {10, 9, 2, 5, 3, 7, 101, 18}),
                LongestIncreasingSubsequence.tabulated(new int[] {10, 9, 2, 5, 3, 7, 101, 18}),
                LongestIncreasingSubsequence.spaceOptimized(new int[] {10, 9, 2, 5, 3, 7, 101, 18}),
                EditDistance.memoized("kitten", "sitting"),
                EditDistance.tabulated("kitten", "sitting"),
                EditDistance.spaceOptimized("kitten", "sitting"),
                MatrixChainMultiplication.memoized(new int[] {40, 20, 30, 10, 30}),
                MatrixChainMultiplication.tabulated(new int[] {40, 20, 30, 10, 30}),
                MatrixChainMultiplication.spaceOptimized(new int[] {40, 20, 30, 10, 30}),
                CoinChange.memoized(new int[] {1, 3, 4}, 6),
                CoinChange.tabulated(new int[] {1, 3, 4}, 6),
                CoinChange.spaceOptimized(new int[] {1, 3, 4}, 6),
                RodCutting.memoized(new int[] {1, 5, 8, 9, 10, 17, 17, 20}, 8),
                RodCutting.tabulated(new int[] {1, 5, 8, 9, 10, 17, 17, 20}, 8),
                RodCutting.spaceOptimized(new int[] {1, 5, 8, 9, 10, 17, 17, 20}, 8),
                LongestPalindromicSubstring.memoized("babad"),
                LongestPalindromicSubstring.tabulated("babad"),
                LongestPalindromicSubstring.spaceOptimized("babad"),
                EggDrop.memoized(2, 10),
                EggDrop.tabulated(2, 10),
                EggDrop.spaceOptimized(2, 10),
                OptimalBinarySearchTree.memoized(new String[] {"A", "B", "C"}, new int[] {34, 8, 50}),
                OptimalBinarySearchTree.tabulated(new String[] {"A", "B", "C"}, new int[] {34, 8, 50}),
                OptimalBinarySearchTree.spaceOptimized(new String[] {"A", "B", "C"}, new int[] {34, 8, 50}),
                TravelingSalesman.memoized(sampleTsp()),
                TravelingSalesman.tabulated(sampleTsp()),
                TravelingSalesman.spaceOptimized(sampleTsp()),
                Fibonacci.memoized(10),
                Fibonacci.tabulated(10),
                Fibonacci.spaceOptimized(10),
                ClimbingStairs.memoized(5),
                ClimbingStairs.tabulated(5),
                ClimbingStairs.spaceOptimized(5),
                UniquePaths.memoized(3, 7),
                UniquePaths.tabulated(3, 7),
                UniquePaths.spaceOptimized(3, 7),
                MinimumPathSum.memoized(sampleGrid()),
                MinimumPathSum.tabulated(sampleGrid()),
                MinimumPathSum.spaceOptimized(sampleGrid()),
                SubsetSum.memoized(new int[] {3, 34, 4, 12, 5, 2}, 9),
                SubsetSum.tabulated(new int[] {3, 34, 4, 12, 5, 2}, 9),
                SubsetSum.spaceOptimized(new int[] {3, 34, 4, 12, 5, 2}, 9),
                EqualPartition.memoized(new int[] {1, 5, 11, 5}),
                EqualPartition.tabulated(new int[] {1, 5, 11, 5}),
                EqualPartition.spaceOptimized(new int[] {1, 5, 11, 5}),
                LongestCommonSubstring.memoized("ABABC", "BABCA"),
                LongestCommonSubstring.tabulated("ABABC", "BABCA"),
                LongestCommonSubstring.spaceOptimized("ABABC", "BABCA"),
                LongestPalindromicSubsequence.memoized("bbbab"),
                LongestPalindromicSubsequence.tabulated("bbbab"),
                LongestPalindromicSubsequence.spaceOptimized("bbbab"),
                WordBreak.memoized("leetcode", Set.of("leet", "code")),
                WordBreak.tabulated("leetcode", Set.of("leet", "code")),
                WordBreak.spaceOptimized("leetcode", Set.of("leet", "code")),
                HouseRobber.memoized(new int[] {2, 7, 9, 3, 1}),
                HouseRobber.tabulated(new int[] {2, 7, 9, 3, 1}),
                HouseRobber.spaceOptimized(new int[] {2, 7, 9, 3, 1})
        );

        System.out.println("Dynamic Programming Demo");
        System.out.println("============================");
        results.forEach(result -> System.out.println(result.describe()));
    }

    private static int[][] sampleTsp() {
        return new int[][] {
                {0, 10, 15, 20},
                {10, 0, 35, 25},
                {15, 35, 0, 30},
                {20, 25, 30, 0}
        };
    }

    private static int[][] sampleGrid() {
        return new int[][] {
                {1, 3, 1},
                {1, 5, 1},
                {4, 2, 1}
        };
    }
}
