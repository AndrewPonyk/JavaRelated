# Backtracking Algorithms — Project Plan

## 1.1 Project File Structure

```
6-Backtracking/
├── docs/
│   ├── PROJECT-PLAN.md          # This file
│   ├── ARCHITECTURE.md          # Architecture & data-flow diagrams
│   └── TECH-NOTES.md            # CI/CD, testing, deployment notes
│
├── java/
│   └── src/
│       ├── Main.java                  # Entry point — menu-driven runner (18 algorithms)
│       ├── nqueens/
│       │   └── NQueens.java
│       ├── sudoku/
│       │   └── SudokuSolver.java
│       ├── hamiltonian/
│       │   └── HamiltonianPath.java
│       ├── graphcoloring/
│       │   └── GraphColoring.java
│       ├── subsetsum/
│       │   └── SubsetSum.java
│       ├── permutations/
│       │   └── Permutations.java
│       ├── combinations/
│       │   └── Combinations.java
│       ├── knightstour/
│       │   └── KnightsTour.java
│       ├── generateparentheses/
│       │   └── GenerateParentheses.java
│       ├── lettercombinations/
│       │   └── LetterCombinations.java
│       ├── combinationsum/
│       │   └── CombinationSum.java
│       ├── palindromepartitioning/
│       │   └── PalindromePartitioning.java
│       ├── restoreip/
│       │   └── RestoreIpAddresses.java
│       ├── ratinmaze/
│       │   └── RatInMaze.java
│       ├── wordsearch/
│       │   └── WordSearch.java
│       ├── boggle/
│       │   └── BoggleSolver.java
│       ├── tugofwar/
│       │   └── TugOfWar.java
│       └── cryptarithmetic/
│           └── Cryptarithmetic.java
│
├── python/
│   ├── main.py                        # Entry point — menu-driven runner (18 algorithms)
│   ├── nqueens.py
│   ├── sudoku_solver.py
│   ├── hamiltonian_path.py
│   ├── graph_coloring.py
│   ├── subset_sum.py
│   ├── permutations.py
│   ├── combinations.py
│   ├── knights_tour.py
│   ├── generate_parentheses.py
│   ├── letter_combinations.py
│   ├── combination_sum.py
│   ├── palindrome_partitioning.py
│   ├── restore_ip.py
│   ├── rat_in_maze.py
│   ├── word_search.py
│   ├── boggle_solver.py
│   ├── tug_of_war.py
│   └── cryptarithmetic.py
│
├── .github/
│   └── workflows/
│       └── ci.yml                     # Lint + compile + test pipeline
│
├── .env.example                       # Environment variable template
├── requirements.txt                   # Python dependencies (none required)
└── README.md                          # Quick-start guide
```

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority)
- [x] Create directory structure
- [x] Java: `Main.java` menu runner with ASCII banner
- [x] Python: `main.py` menu runner with ASCII banner
- [x] Implement **N-Queens** (Java + Python)
- [x] Implement **Sudoku Solver** (Java + Python)
- [x] Implement **Knight's Tour** (Java + Python)
- [x] Implement **Hamiltonian Path** (Java + Python)
- [x] Implement **Graph Coloring** (Java + Python)
- [x] Implement **Subset Sum** (Java + Python)
- [x] Implement **Permutations** (Java + Python)
- [x] Implement **Combinations** (Java + Python)

### Phase 2 — Extended algorithms (medium priority)
- [x] Implement **Generate Parentheses** (Java + Python)
- [x] Implement **Letter Combinations** (Java + Python)
- [x] Implement **Combination Sum** (Java + Python)
- [x] Implement **Palindrome Partitioning** (Java + Python)
- [x] Implement **Restore IP Addresses** (Java + Python)
- [x] Implement **Rat in a Maze** (Java + Python)
- [x] Implement **Word Search** (Java + Python)
- [x] Implement **Boggle Solver** (Java + Python)
- [x] Implement **Tug of War** (Java + Python)
- [x] Implement **Cryptarithmetic** (Java + Python)

### Phase 3 — Polish & optimization (lower priority)
- [ ] Add execution-time measurement to every algorithm
- [ ] Add step-by-step verbose mode (`-v` flag)
- [ ] Add unit tests (JUnit 5 / pytest)
- [ ] Add CI pipeline (compile + test on push)
- [ ] Polish console output with boxed tables and color codes
