# Dynamic Programming Algorithms Project Plan

## 1.1 Project File Structure

This project is a local learning workspace for 21 dynamic programming algorithms in Java and Python. It is intentionally a layered console application rather than a web system: the "frontend" is the command-line demo layer, the "backend" is the algorithm/service layer, and persistence is optional metadata for learning runs.

```text
3-Dynamic-Programming/
  docs/
    PROJECT-PLAN.md
    ARCHITECTURE.md
    TECH-NOTES.md
  .github/
    workflows/
      ci.yml
  config/
    logging.properties
  migrations/
    001_algorithm_runs.sql
  scripts/
    run-java.ps1
    run-python.ps1
    test.ps1
  python/
    dp_algorithms/
      __init__.py
      common.py
      cli.py
      demo.py
      knapsack.py
      lcs.py
      lis.py
      edit_distance.py
      matrix_chain.py
      coin_change.py
      rod_cutting.py
      longest_palindrome.py
      egg_drop.py
      optimal_bst.py
      tsp.py
      fibonacci.py
      climbing_stairs.py
      unique_paths.py
      minimum_path_sum.py
      subset_sum.py
      equal_partition.py
      longest_common_substring.py
      longest_palindromic_subsequence.py
      word_break.py
      house_robber.py
    tests/
      test_dp_algorithms.py
  java/
    pom.xml
    config/
      checkstyle.xml
    src/
      main/java/com/example/dp/
        App.java
        Result.java
        Knapsack.java
        LongestCommonSubsequence.java
        LongestIncreasingSubsequence.java
        EditDistance.java
        MatrixChainMultiplication.java
        CoinChange.java
        RodCutting.java
        LongestPalindromicSubstring.java
        EggDrop.java
        OptimalBinarySearchTree.java
        TravelingSalesman.java
        Fibonacci.java
        ClimbingStairs.java
        UniquePaths.java
        MinimumPathSum.java
        SubsetSum.java
        EqualPartition.java
        LongestCommonSubstring.java
        LongestPalindromicSubsequence.java
        WordBreak.java
        HouseRobber.java
      test/java/com/example/dp/
        DynamicProgrammingSmokeTest.java
  .editorconfig
  .env.example
  .gitignore
  pyproject.toml
  requirements-dev.txt
  README.md
```

### Source Code Layout

- `python/dp_algorithms`: Python algorithm implementations, CLI entry points, and shared result objects.
- `java/src/main/java/com/example/dp`: Java algorithm implementations and console runner.
- `python/tests` and `java/src/test`: focused unit and smoke tests.
- `migrations`: optional SQL schema for storing demo runs and results if this learning project is later connected to a database.
- `config`: local runtime configuration placeholders.

### CI/CD Layout

- `.github/workflows/ci.yml`: local-friendly CI that installs Python dependencies, runs Python tests, builds Java with Maven, and runs Java tests.
- No deployment pipeline is included because the target platform is local execution.
- No Docker files are included because this algorithm project does not require containerization.

### Tool Configuration

- `pyproject.toml`: Python formatter, linter, and pytest settings.
- `requirements-dev.txt`: Python development dependencies.
- `java/pom.xml`: Maven project, compiler, JUnit, and exec plugin.
- `java/config/checkstyle.xml`: Java style baseline.
- `.editorconfig`: cross-editor formatting defaults.
- `.env.example`: local environment template.

## 1.2 Implementation TODO List

### Phase 1: Foundation - High Priority

- [x] Create project directories for docs, Java, Python, tests, scripts, config, migrations, and CI.
- [x] Add project plan, architecture notes, and technical notes.
- [x] Add runnable console demos for Java and Python.
- [x] Add baseline tests for representative algorithms.
- [x] Add local scripts for running and testing.

### Phase 2: Core Features - Medium Priority

- [x] Implement memoization and tabulation variants for 21 dynamic programming problems.
- [x] Include space-optimized variants where the algorithm naturally supports them.
- [x] Include backtracking or reconstruction outputs where meaningful.
- [x] Keep console output readable for learning and comparison.
- [ ] Expand unit tests to cover edge cases for every algorithm.
- [ ] Add benchmark mode for comparing memoized, tabulated, and optimized variants.

### Phase 3: Polish & Optimization - Lower Priority

- [ ] Add Markdown examples for each algorithm with complexity notes.
- [ ] Add optional persistence adapter using `migrations/001_algorithm_runs.sql`.
- [ ] Add richer CLI filtering, for example `--algorithm lcs` or `--variant memoized`.
- [ ] Add generated complexity summary tables.
- [ ] Add mutation or property-based tests for selected algorithms.
