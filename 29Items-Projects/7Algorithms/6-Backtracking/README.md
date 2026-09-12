# Backtracking Algorithms Suite

A collection of 18 classic backtracking algorithms implemented in **Java** and **Python**, with an interactive menu-driven runner and execution timing.

## Algorithms

| # | Algorithm | Description | Java Package | Python Module |
|---|-----------|-------------|-------------|---------------|
| 1 | N-Queens | Place n queens on an n×n board with no attacks | `nqueens` | `nqueens.py` |
| 2 | Sudoku Solver | Solve 9×9 Sudoku puzzles | `sudoku` | `sudoku_solver.py` |
| 3 | Hamiltonian Path | Find path visiting every vertex once | `hamiltonian` | `hamiltonian_path.py` |
| 4 | Graph Coloring | Color graph vertices with min colors | `graphcoloring` | `graph_coloring.py` |
| 5 | Subset Sum | Find subsets summing to target | `subsetsum` | `subset_sum.py` |
| 6 | Permutations | Generate all permutations | `permutations` | `permutations.py` |
| 7 | Combinations | Generate all k-sized combinations | `combinations` | `combinations.py` |
| 8 | Knight's Tour | Find knight's path covering entire board | `knightstour` | `knights_tour.py` |
| 9 | Generate Parentheses | All valid n-pair parentheses combos | `generateparentheses` | `generate_parentheses.py` |
| 10 | Letter Combinations | T9 phone number letter mapping | `lettercombinations` | `letter_combinations.py` |
| 11 | Combination Sum | Unique combos summing to target (reuse ok) | `combinationsum` | `combination_sum.py` |
| 12 | Palindrome Partitioning | Split string into palindrome substrings | `palindromepartitioning` | `palindrome_partitioning.py` |
| 13 | Restore IP Addresses | Valid IP splits from digit string | `restoreip` | `restore_ip.py` |
| 14 | Rat in a Maze | Find path through maze grid | `ratinmaze` | `rat_in_maze.py` |
| 15 | Word Search | Find words in 2D letter grid | `wordsearch` | `word_search.py` |
| 16 | Boggle Solver | Dictionary words on Boggle board | `boggle` | `boggle_solver.py` |
| 17 | Tug of War | Partition set with min difference | `tugofwar` | `tug_of_war.py` |
| 18 | Cryptarithmetic | Solve SEND + MORE = MONEY | `cryptarithmetic` | `cryptarithmetic.py` |

## Quick Start

### Java

```bash
cd java/src
javac -encoding UTF-8 -d ../out Main.java nqueens/NQueens.java sudoku/SudokuSolver.java hamiltonian/HamiltonianPath.java graphcoloring/GraphColoring.java subsetsum/SubsetSum.java permutations/Permutations.java combinations/Combinations.java knightstour/KnightsTour.java generateparentheses/GenerateParentheses.java lettercombinations/LetterCombinations.java combinationsum/CombinationSum.java palindromepartitioning/PalindromePartitioning.java restoreip/RestoreIpAddresses.java ratinmaze/RatInMaze.java wordsearch/WordSearch.java boggle/BoggleSolver.java tugofwar/TugOfWar.java cryptarithmetic/Cryptarithmetic.java
java -cp ../out Main
```

### Python

```bash
cd python
python main.py
```

## Project Structure

```
6-Backtracking/
├── java/src/          # 18 algorithm packages + Main.java runner
├── python/            # 18 algorithm modules + main.py runner
├── docs/              # Architecture, project plan, tech notes
└── .github/workflows/ # CI pipeline
```
