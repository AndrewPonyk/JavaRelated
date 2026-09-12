"""Backtracking Algorithms Suite — Interactive menu runner."""

from nqueens import NQueens
from sudoku_solver import SudokuSolver
from hamiltonian_path import HamiltonianPath
from graph_coloring import GraphColoring
from subset_sum import SubsetSum
from permutations import Permutations
from combinations import Combinations
from knights_tour import KnightsTour
from generate_parentheses import GenerateParentheses
from letter_combinations import LetterCombinations
from combination_sum import CombinationSum
from palindrome_partitioning import PalindromePartitioning
from restore_ip import RestoreIpAddresses
from rat_in_maze import RatInMaze
from word_search import WordSearch
from boggle_solver import BoggleSolver
from tug_of_war import TugOfWar
from cryptarithmetic import Cryptarithmetic
import time


def box(text: str) -> str:
    line = "─" * (len(text) + 4)
    return f"┌{line}┐\n│  {text}  │\n└{line}┘"


def timed(label: str, fn):
    print(f"\n{box(label)}")
    start = time.perf_counter()
    fn()
    elapsed = (time.perf_counter() - start) * 1000
    print(f"Time: {elapsed:.1f} ms\n")


def read_int(prompt: str, default: int) -> int:
    try:
        val = input(prompt).strip()
        return default if val == "" else int(val)
    except (ValueError, EOFError):
        return default


BANNER = """
╔══════════════════════════════════════════╗
║      BACKTRACKING ALGORITHMS SUITE       ║
╚══════════════════════════════════════════╝
"""

MENU = """
 1. N-Queens
 2. Sudoku Solver
 3. Hamiltonian Path
 4. Graph Coloring
 5. Subset Sum
 6. Permutations
 7. Combinations
 8. Knight's Tour
 9. Generate Parentheses
10. Letter Combinations (Phone)
11. Combination Sum
12. Palindrome Partitioning
13. Restore IP Addresses
14. Rat in a Maze
15. Word Search
16. Boggle Solver
17. Tug of War
18. Cryptarithmetic
 0. Exit
"""


def run_nqueens():
    n = read_int("Board size (default 8): ", 8)
    timed(f"N-Queens (n={n})", lambda: NQueens(n).solve_and_print())


def run_sudoku():
    board = [
        [5, 3, 0, 0, 7, 0, 0, 0, 0],
        [6, 0, 0, 1, 9, 5, 0, 0, 0],
        [0, 9, 8, 0, 0, 0, 0, 6, 0],
        [8, 0, 0, 0, 6, 0, 0, 0, 3],
        [4, 0, 0, 8, 0, 3, 0, 0, 1],
        [7, 0, 0, 0, 2, 0, 0, 0, 6],
        [0, 6, 0, 0, 0, 0, 2, 8, 0],
        [0, 0, 0, 4, 1, 9, 0, 0, 5],
        [0, 0, 0, 0, 8, 0, 0, 7, 9],
    ]
    timed("Sudoku", lambda: SudokuSolver(board).solve_and_print())


def run_hamiltonian():
    adj = [
        [0, 1, 1, 1, 0],
        [1, 0, 1, 0, 1],
        [1, 1, 0, 1, 1],
        [1, 0, 1, 0, 1],
        [0, 1, 1, 1, 0],
    ]
    timed("Hamiltonian Path", lambda: HamiltonianPath(adj).solve_and_print())


def run_graph_coloring():
    colors = read_int("Number of colors (default 3): ", 3)
    adj = [[0, 1, 1, 1], [1, 0, 1, 0], [1, 1, 0, 1], [1, 0, 1, 0]]
    timed(f"Graph Coloring ({colors} colors)", lambda: GraphColoring(adj, colors).solve_and_print())


def run_subset_sum():
    target = read_int("Target sum (default 9): ", 9)
    timed(f"Subset Sum (target={target})", lambda: SubsetSum([3, 34, 4, 12, 5, 2], target).solve_and_print())


def run_permutations():
    timed("Permutations", lambda: Permutations([1, 2, 3]).solve_and_print())


def run_combinations():
    k = read_int("Combination size k (default 2): ", 2)
    timed(f"Combinations (k={k})", lambda: Combinations([1, 2, 3, 4], k).solve_and_print())


def run_knights_tour():
    n = read_int("Board size (default 5): ", 5)
    timed(f"Knight's Tour (n={n})", lambda: KnightsTour(n).solve_and_print())


def run_generate_parentheses():
    n = read_int("Number of pairs (default 4): ", 4)
    timed(f"Generate Parentheses (n={n})", lambda: GenerateParentheses(n).solve_and_print())


def run_letter_combinations():
    digits = input("Enter digits (default 23): ").strip() or "23"
    timed(f'Letter Combinations ("{digits}")', lambda: LetterCombinations(digits).solve_and_print())


def run_combination_sum():
    target = read_int("Target sum (default 7): ", 7)
    timed(f"Combination Sum (target={target})", lambda: CombinationSum([2, 3, 6, 7], target).solve_and_print())


def run_palindrome_partitioning():
    s = input("Enter string (default aab): ").strip() or "aab"
    timed(f'Palindrome Partitioning ("{s}")', lambda: PalindromePartitioning(s).solve_and_print())


def run_restore_ip():
    s = input("Enter digit string (default 25525511135): ").strip() or "25525511135"
    timed(f'Restore IP Addresses ("{s}")', lambda: RestoreIpAddresses(s).solve_and_print())


def run_rat_in_maze():
    maze = [[1, 0, 0, 0], [1, 1, 0, 1], [0, 1, 0, 0], [1, 1, 1, 1]]
    timed("Rat in a Maze (4x4)", lambda: RatInMaze(maze).solve_and_print())


def run_word_search():
    board = [["A", "B", "C", "E"], ["S", "F", "C", "S"], ["A", "D", "E", "E"]]
    words = ["ABCCED", "SEE", "ABCB"]
    timed("Word Search", lambda: WordSearch(board, words).solve_and_print())


def run_boggle():
    board = [["G", "I", "Z"], ["U", "E", "K"], ["Q", "S", "E"]]
    dictionary = {"geeks", "quiz", "for", "seek", "see"}
    timed("Boggle Solver (3x3)", lambda: BoggleSolver(board, dictionary).solve_and_print())


def run_tug_of_war():
    timed("Tug of War", lambda: TugOfWar([3, 1, 4, 2, 2, 1]).solve_and_print())


def run_cryptarithmetic():
    w1 = input("Word 1 (default SEND): ").strip() or "SEND"
    w2 = input("Word 2 (default MORE): ").strip() or "MORE"
    res = input("Result (default MONEY): ").strip() or "MONEY"
    timed(f"{w1} + {w2} = {res}", lambda: Cryptarithmetic(w1, w2, res).solve_and_print())


DISPATCH = {
    "1": run_nqueens,
    "2": run_sudoku,
    "3": run_hamiltonian,
    "4": run_graph_coloring,
    "5": run_subset_sum,
    "6": run_permutations,
    "7": run_combinations,
    "8": run_knights_tour,
    "9": run_generate_parentheses,
    "10": run_letter_combinations,
    "11": run_combination_sum,
    "12": run_palindrome_partitioning,
    "13": run_restore_ip,
    "14": run_rat_in_maze,
    "15": run_word_search,
    "16": run_boggle,
    "17": run_tug_of_war,
    "18": run_cryptarithmetic,
}


def main():
    while True:
        print(BANNER)
        print(MENU)
        choice = input("Choose algorithm (0 to quit): ").strip()
        if choice == "0":
            print("Goodbye!")
            break
        func = DISPATCH.get(choice)
        if func:
            try:
                func()
            except Exception as e:
                print(f"Error: {e}")
            input("\nPress Enter to continue...")
        else:
            print("Invalid choice.")


if __name__ == "__main__":
    main()
