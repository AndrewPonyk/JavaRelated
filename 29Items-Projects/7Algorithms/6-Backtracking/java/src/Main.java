import java.util.*;
import java.util.stream.*;

public class Main {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printBanner();
            printMenu();
            System.out.print("Choose algorithm (0 to quit): ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1": runNQueens(sc);           break;
                case "2": runSudoku(sc);            break;
                case "3": runHamiltonian(sc);       break;
                case "4": runGraphColoring(sc);     break;
                case "5": runSubsetSum(sc);         break;
                case "6": runPermutations(sc);      break;
                case "7": runCombinations(sc);      break;
                case "8": runKnightsTour(sc);       break;
                case "9": runGenerateParentheses(sc);  break;
                case "10": runLetterCombinations(sc);  break;
                case "11": runCombinationSum(sc);      break;
                case "12": runPalindromePartitioning(sc); break;
                case "13": runRestoreIp(sc);           break;
                case "14": runRatInMaze(sc);           break;
                case "15": runWordSearch(sc);          break;
                case "16": runBoggle(sc);              break;
                case "17": runTugOfWar(sc);            break;
                case "18": runCryptarithmetic(sc);     break;
                case "0": running = false;              break;
                default:  System.out.println("Invalid choice.");
            }
            if (running) {
                System.out.println("\nPress Enter to continue...");
                sc.nextLine();
            }
        }
        System.out.println("Goodbye!");
    }

    private static void runNQueens(Scanner sc) {
        System.out.print("Board size (default 8): ");
        int n = readInt(sc, 8);
        nqueens.NQueens solver = new nqueens.NQueens(n);
        timeAndPrint("N-Queens (n=" + n + ")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runSudoku(Scanner sc) {
        int[][] board = {
            {5,3,0,0,7,0,0,0,0},
            {6,0,0,1,9,5,0,0,0},
            {0,9,8,0,0,0,0,6,0},
            {8,0,0,0,6,0,0,0,3},
            {4,0,0,8,0,3,0,0,1},
            {7,0,0,0,2,0,0,0,6},
            {0,6,0,0,0,0,2,8,0},
            {0,0,0,4,1,9,0,0,5},
            {0,0,0,0,8,0,0,7,9}
        };
        sudoku.SudokuSolver solver = new sudoku.SudokuSolver(board);
        timeAndPrint("Sudoku", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runHamiltonian(Scanner sc) {
        int[][] adj = {
            {0,1,1,1,0},
            {1,0,1,0,1},
            {1,1,0,1,1},
            {1,0,1,0,1},
            {0,1,1,1,0}
        };
        hamiltonian.HamiltonianPath solver = new hamiltonian.HamiltonianPath(adj);
        timeAndPrint("Hamiltonian Path", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runGraphColoring(Scanner sc) {
        System.out.print("Number of colors (default 3): ");
        int colors = readInt(sc, 3);
        int[][] adj = {
            {0,1,1,1},
            {1,0,1,0},
            {1,1,0,1},
            {1,0,1,0}
        };
        graphcoloring.GraphColoring solver = new graphcoloring.GraphColoring(adj, colors);
        timeAndPrint("Graph Coloring (" + colors + " colors)", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runSubsetSum(Scanner sc) {
        int[] set = {3, 34, 4, 12, 5, 2};
        System.out.print("Target sum (default 9): ");
        int target = readInt(sc, 9);
        subsetsum.SubsetSum solver = new subsetsum.SubsetSum(set, target);
        timeAndPrint("Subset Sum (target=" + target + ")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runPermutations(Scanner sc) {
        System.out.print("Enter elements separated by spaces (default: 1 2 3): ");
        String line = sc.nextLine().trim();
        int[] arr;
        if (line.isEmpty()) {
            arr = new int[]{1, 2, 3};
        } else {
            arr = Arrays.stream(line.split("\\s+"))
                        .mapToInt(Integer::parseInt).toArray();
        }
        permutations.Permutations solver = new permutations.Permutations(arr);
        timeAndPrint("Permutations", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runCombinations(Scanner sc) {
        int[] set = {1, 2, 3, 4};
        System.out.print("Combination size k (default 2): ");
        int k = readInt(sc, 2);
        combinations.Combinations solver = new combinations.Combinations(set, k);
        timeAndPrint("Combinations (k=" + k + ")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runKnightsTour(Scanner sc) {
        System.out.print("Board size (default 5): ");
        int n = readInt(sc, 5);
        knightstour.KnightsTour solver = new knightstour.KnightsTour(n);
        timeAndPrint("Knight's Tour (n=" + n + ")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runGenerateParentheses(Scanner sc) {
        System.out.print("Number of pairs (default 4): ");
        int n = readInt(sc, 4);
        generateparentheses.GenerateParentheses solver = new generateparentheses.GenerateParentheses(n);
        timeAndPrint("Generate Parentheses (n=" + n + ")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runLetterCombinations(Scanner sc) {
        System.out.print("Enter digits (default 23): ");
        String digits = sc.nextLine().trim();
        if (digits.isEmpty()) digits = "23";
        lettercombinations.LetterCombinations solver = new lettercombinations.LetterCombinations(digits);
        timeAndPrint("Letter Combinations (\"" + digits + "\")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runCombinationSum(Scanner sc) {
        int[] candidates = {2, 3, 6, 7};
        System.out.print("Target sum (default 7): ");
        int target = readInt(sc, 7);
        combinationsum.CombinationSum solver = new combinationsum.CombinationSum(candidates, target);
        timeAndPrint("Combination Sum (target=" + target + ")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runPalindromePartitioning(Scanner sc) {
        System.out.print("Enter string (default aab): ");
        String s = sc.nextLine().trim();
        if (s.isEmpty()) s = "aab";
        palindromepartitioning.PalindromePartitioning solver = new palindromepartitioning.PalindromePartitioning(s);
        timeAndPrint("Palindrome Partitioning (\"" + s + "\")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runRestoreIp(Scanner sc) {
        System.out.print("Enter digit string (default 25525511135): ");
        String s = sc.nextLine().trim();
        if (s.isEmpty()) s = "25525511135";
        restoreip.RestoreIpAddresses solver = new restoreip.RestoreIpAddresses(s);
        timeAndPrint("Restore IP Addresses (\"" + s + "\")", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runRatInMaze(Scanner sc) {
        int[][] maze = {
            {1, 0, 0, 0},
            {1, 1, 0, 1},
            {0, 1, 0, 0},
            {1, 1, 1, 1}
        };
        ratinmaze.RatInMaze solver = new ratinmaze.RatInMaze(maze);
        timeAndPrint("Rat in a Maze (4x4)", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runWordSearch(Scanner sc) {
        char[][] board = {
            {'A','B','C','E'},
            {'S','F','C','S'},
            {'A','D','E','E'}
        };
        java.util.List<String> words = java.util.List.of("ABCCED", "SEE", "ABCB");
        wordsearch.WordSearch solver = new wordsearch.WordSearch(board, words);
        timeAndPrint("Word Search", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runBoggle(Scanner sc) {
        char[][] board = {
            {'G','I','Z'},
            {'U','E','K'},
            {'Q','S','E'}
        };
        java.util.Set<String> dict = new java.util.HashSet<>(
            java.util.List.of("geeks", "quiz", "for", "seek", "see"));
        boggle.BoggleSolver solver = new boggle.BoggleSolver(board, dict);
        timeAndPrint("Boggle Solver (3x3)", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runTugOfWar(Scanner sc) {
        int[] arr = {3, 1, 4, 2, 2, 1};
        tugofwar.TugOfWar solver = new tugofwar.TugOfWar(arr);
        timeAndPrint("Tug of War", new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static void runCryptarithmetic(Scanner sc) {
        System.out.print("Word 1 (default SEND): ");
        String w1 = sc.nextLine().trim();
        if (w1.isEmpty()) w1 = "SEND";
        System.out.print("Word 2 (default MORE): ");
        String w2 = sc.nextLine().trim();
        if (w2.isEmpty()) w2 = "MORE";
        System.out.print("Result (default MONEY): ");
        String res = sc.nextLine().trim();
        if (res.isEmpty()) res = "MONEY";
        cryptarithmetic.Cryptarithmetic solver = new cryptarithmetic.Cryptarithmetic(w1, w2, res);
        timeAndPrint(w1 + " + " + w2 + " = " + res, new Runnable() {
            public void run() { solver.solveAndPrint(); }
        });
    }

    private static int readInt(Scanner sc, int defaultVal) {
        try {
            String line = sc.nextLine().trim();
            return line.isEmpty() ? defaultVal : Integer.parseInt(line);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static void timeAndPrint(String label, Runnable task) {
        System.out.println("\n" + box(label));
        long start = System.nanoTime();
        task.run();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Time: " + elapsed + " ms\n");
    }

    private static String box(String text) {
        String line = repeat("-", text.length() + 4);
        return "+" + line + "+\n|  " + text + "  |\n+" + line + "+";
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }

    private static void printBanner() {
        System.out.println("+==========================================+");
        System.out.println("|      BACKTRACKING ALGORITHMS SUITE       |");
        System.out.println("+==========================================+");
        System.out.println();
    }

    private static void printMenu() {
        System.out.println(" 1. N-Queens");
        System.out.println(" 2. Sudoku Solver");
        System.out.println(" 3. Hamiltonian Path");
        System.out.println(" 4. Graph Coloring");
        System.out.println(" 5. Subset Sum");
        System.out.println(" 6. Permutations");
        System.out.println(" 7. Combinations");
        System.out.println(" 8. Knight's Tour");
        System.out.println(" 9. Generate Parentheses");
        System.out.println("10. Letter Combinations (Phone)");
        System.out.println("11. Combination Sum");
        System.out.println("12. Palindrome Partitioning");
        System.out.println("13. Restore IP Addresses");
        System.out.println("14. Rat in a Maze");
        System.out.println("15. Word Search");
        System.out.println("16. Boggle Solver");
        System.out.println("17. Tug of War");
        System.out.println("18. Cryptarithmetic");
        System.out.println(" 0. Exit");
        System.out.println();
    }
}
