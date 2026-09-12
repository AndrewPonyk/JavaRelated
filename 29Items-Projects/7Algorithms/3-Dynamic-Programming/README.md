# Dynamic Programming Algorithms

Java and Python implementations of core dynamic programming problems for learning and comparison.

Each algorithm is implemented in both languages and demonstrated through console output. The project focuses on:

- Memoization: top-down recursion with cached states.
- Tabulation: bottom-up dynamic programming tables.
- Space optimization: rolling arrays, one-dimensional DP, or compact state variants where practical.
- Backtracking/reconstruction: selected items, paths, strings, cuts, tours, or edit operations where meaningful.

Docker is not required for this project.

## Algorithms

| # | Algorithm | DP Pattern | What It Demonstrates |
|---|---|---|---|
| 1 | 0/1 Knapsack | Capacity DP | Maximize value under a weight limit; reconstruct selected items. |
| 2 | Longest Common Subsequence | Sequence DP | Longest ordered subsequence shared by two strings. |
| 3 | Longest Increasing Subsequence | Sequence DP | Longest strictly increasing subsequence; includes patience-style optimization. |
| 4 | Edit Distance | String DP | Minimum insert/delete/replace operations between two strings. |
| 5 | Matrix Chain Multiplication | Interval DP | Minimum multiplication cost and optimal parenthesization. |
| 6 | Coin Change | Unbounded DP | Minimum number of coins needed for an amount. |
| 7 | Rod Cutting | Unbounded DP | Maximum revenue from cutting a rod. |
| 8 | Longest Palindromic Substring | String DP | Longest contiguous palindrome. |
| 9 | Egg Drop | Decision DP | Minimum attempts needed in the worst case. |
| 10 | Optimal Binary Search Tree | Interval DP | Minimum expected search cost from key frequencies. |
| 11 | Traveling Salesman Problem | Bitmask DP | Minimum Hamiltonian tour for a small complete graph. |
| 12 | Fibonacci Number | Linear DP | Classic recurrence and rolling-state optimization. |
| 13 | Climbing Stairs | Linear DP | Count ways to climb using one or two steps. |
| 14 | Unique Paths | Grid DP | Count paths through a grid using right/down moves. |
| 15 | Minimum Path Sum | Grid DP | Find cheapest path through a weighted grid. |
| 16 | Subset Sum | Subset DP | Determine whether a subset reaches a target sum. |
| 17 | Equal Partition | Subset DP | Determine whether numbers split into equal-sum subsets. |
| 18 | Longest Common Substring | String DP | Longest contiguous string shared by two strings. |
| 19 | Longest Palindromic Subsequence | Interval/String DP | Longest non-contiguous palindromic sequence. |
| 20 | Word Break | String DP | Determine whether a string can be segmented into dictionary words. |
| 21 | House Robber | Linear DP | Maximize sum while skipping adjacent houses. |

## Project Structure

```text
3-Dynamic-Programming/
  docs/                  Architecture, project plan, and technical notes
  python/dp_algorithms/   Python implementations and demo entry point
  python/tests/           Python tests
  java/                   Maven project with Java implementations and tests
  scripts/                PowerShell helpers for running demos and tests
  migrations/             Optional SQL schema for future run history storage
  config/                 Logging/config placeholders
```

## Prerequisites

- Python 3.11 or newer
- Java 11 or newer
- Maven 3.8 or newer
- PowerShell on Windows for the helper scripts

The current Maven project targets Java 11 because this workspace has JDK 11 installed.

## Run Python Demo

From this directory:

```powershell
.\scripts\run-python.ps1
```

Equivalent manual command:

```powershell
$env:PYTHONPATH = "$PWD\python"
python -m dp_algorithms.demo
```

## Run Java Demo

```powershell
.\scripts\run-java.ps1
```

The Java script compiles the Maven project and then runs:

```powershell
java -cp "java\target\classes" com.example.dp.App
```

## Run Tests

```powershell
.\scripts\test.ps1
```

Run only Python tests:

```powershell
pytest python/tests
```

Run only Java tests:

```powershell
cd java
mvn test
```

## Expected Console Output

Both demos print each algorithm and variant, for example:

```text
0/1 Knapsack [tabulation] -> 12 | items=[1, 3]
LCS [tabulation] -> 4 | sequence=GTAB
Edit Distance [tabulation] -> 3 | operations=[replace k->s, replace e->i, insert g]
Minimum Path Sum [tabulation] -> 7 | path=[...]
Traveling Salesman [tabulation/bitmask] -> 80 | tour=[0, 2, 3, 1, 0]
```

## Notes

- Some algorithms cannot preserve reconstruction data in their most compact form without extra state, so the space-optimized variant may return only the optimal value.
- TSP and Egg Drop use intentionally small demo inputs because their state spaces grow quickly.
- The implementations prioritize readability and learning value over micro-optimizations.
