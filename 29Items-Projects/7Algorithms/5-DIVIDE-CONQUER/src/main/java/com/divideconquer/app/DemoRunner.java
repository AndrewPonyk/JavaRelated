package com.divideconquer.app;

import com.divideconquer.algorithms.BinarySearch;
import com.divideconquer.algorithms.ClosestPair;
import com.divideconquer.algorithms.ConvexHull;
import com.divideconquer.algorithms.CountingInversions;
import com.divideconquer.algorithms.DivideConquerDpOptimization;
import com.divideconquer.algorithms.ExponentiationBySquaring;
import com.divideconquer.algorithms.FastFourierTransform;
import com.divideconquer.algorithms.Karatsuba;
import com.divideconquer.algorithms.MaximumSubarray;
import com.divideconquer.algorithms.MajorityElement;
import com.divideconquer.algorithms.MedianOfMedians;
import com.divideconquer.algorithms.MergeSort;
import com.divideconquer.algorithms.PeakFinding;
import com.divideconquer.algorithms.PolynomialMultiplication;
import com.divideconquer.algorithms.Quickselect;
import com.divideconquer.algorithms.RotatedArraySearch;
import com.divideconquer.algorithms.SegmentTree;
import com.divideconquer.algorithms.SelectionInTwoSortedArrays;
import com.divideconquer.algorithms.SkylineProblem;
import com.divideconquer.algorithms.StrassenMatrix;
import com.divideconquer.algorithms.ToomCookMultiplication;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class DemoRunner {
    private DemoRunner() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                printHelp();
                return 0;
            }

            System.out.println("DIVIDE-CONQUER Java Demo");
            System.out.println("========================");
            if (options.benchmark) {
                runBenchmarks(options);
                return 0;
            }
            runSelected(options.algorithm);
            if (options.trace) {
                printTrace(options.algorithm);
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println("Run with --help for usage.");
            return 2;
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -cp target/classes com.divideconquer.app.DemoRunner [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --algorithm <name>  Run one algorithm: strassen, closest-pair, convex-hull, fft, karatsuba,");
        System.out.println("                      quickselect, median-of-medians, binary-search, merge-sort,");
        System.out.println("                      counting-inversions, maximum-subarray, exponentiation, toom-cook,");
        System.out.println("                      polynomial, skyline, majority, rotated-search, peak-finding,");
        System.out.println("                      two-sorted-selection, dc-dp, segment-tree, fft-convolution, all");
        System.out.println("  --benchmark         Print timing tables for deterministic generated inputs");
        System.out.println("  --sizes <list>      Comma-separated benchmark sizes, default: 16,64,256,1024");
        System.out.println("  --trace             Print visual recursive split/combine traces");
        System.out.println("  --help              Show this message");
    }

    private static void runSelected(String algorithm) {
        switch (algorithm) {
            case "all":
                runStrassen();
                runClosestPair();
                runConvexHull();
                runFft();
                runKaratsuba();
                runQuickselect();
                runMedianOfMedians();
                runBinarySearch();
                runMergeSort();
                runCountingInversions();
                runMaximumSubarray();
                runExponentiation();
                runToomCook();
                runPolynomial();
                runSkyline();
                runMajority();
                runRotatedSearch();
                runPeakFinding();
                runTwoSortedSelection();
                runDivideConquerDp();
                runSegmentTree();
                runFftConvolution();
                break;
            case "strassen":
                runStrassen();
                break;
            case "closest-pair":
                runClosestPair();
                break;
            case "convex-hull":
                runConvexHull();
                break;
            case "fft":
                runFft();
                break;
            case "karatsuba":
                runKaratsuba();
                break;
            case "quickselect":
                runQuickselect();
                break;
            case "median-of-medians":
                runMedianOfMedians();
                break;
            case "binary-search":
                runBinarySearch();
                break;
            case "merge-sort":
                runMergeSort();
                break;
            case "counting-inversions":
                runCountingInversions();
                break;
            case "maximum-subarray":
                runMaximumSubarray();
                break;
            case "exponentiation":
                runExponentiation();
                break;
            case "toom-cook":
                runToomCook();
                break;
            case "polynomial":
                runPolynomial();
                break;
            case "skyline":
                runSkyline();
                break;
            case "majority":
                runMajority();
                break;
            case "rotated-search":
                runRotatedSearch();
                break;
            case "peak-finding":
                runPeakFinding();
                break;
            case "two-sorted-selection":
                runTwoSortedSelection();
                break;
            case "dc-dp":
                runDivideConquerDp();
                break;
            case "segment-tree":
                runSegmentTree();
                break;
            case "fft-convolution":
                runFftConvolution();
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }

    private static void runStrassen() {
        int[][] a = {{1, 2}, {3, 4}};
        int[][] b = {{5, 6}, {7, 8}};
        System.out.println("\nStrassen Matrix Multiplication");
        System.out.println("Result: " + Arrays.deepToString(StrassenMatrix.multiply(a, b)));
        System.out.println("Master theorem: T(n)=7T(n/2)+O(n^2) => O(n^log2 7)");
        System.out.println("Interpretation: seven recursive half-size products beat the eight products of classical splitting.");
    }

    private static void runClosestPair() {
        List<ClosestPair.Point> points = List.of(
                new ClosestPair.Point(0, 0),
                new ClosestPair.Point(5, 4),
                new ClosestPair.Point(3, 1),
                new ClosestPair.Point(9, 6),
                new ClosestPair.Point(3, 2)
        );
        ClosestPair.Result result = ClosestPair.find(points);
        System.out.println("\nClosest Pair");
        System.out.printf("Result: %s <-> %s, distance %.3f%n", result.first(), result.second(), result.distance());
        System.out.println("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)");
        System.out.println("Interpretation: sorted-by-y strip merging keeps the combine step linear.");
    }

    private static void runConvexHull() {
        List<ConvexHull.Point> points = List.of(
                new ConvexHull.Point(0, 0),
                new ConvexHull.Point(1, 1),
                new ConvexHull.Point(2, 0),
                new ConvexHull.Point(2, 2),
                new ConvexHull.Point(0, 2)
        );
        System.out.println("\nConvex Hull");
        System.out.println("Result: " + ConvexHull.monotonicChain(points));
        System.out.println("Strategy: sort then build lower/upper hulls => O(n log n)");
        System.out.println("Interpretation: sorting dominates; each point is pushed and popped at most once.");
    }

    private static void runFft() {
        FastFourierTransform.Complex[] input = {
                new FastFourierTransform.Complex(1, 0),
                new FastFourierTransform.Complex(2, 0),
                new FastFourierTransform.Complex(3, 0),
                new FastFourierTransform.Complex(4, 0)
        };
        System.out.println("\nFast Fourier Transform");
        System.out.println("Result: " + Arrays.toString(FastFourierTransform.fft(input)));
        System.out.println("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)");
        System.out.println("Interpretation: even/odd decomposition leaves linear twiddle-factor combine work.");
    }

    private static void runKaratsuba() {
        System.out.println("\nKaratsuba Multiplication");
        System.out.println("Result: 1234 * 5678 = " + Karatsuba.multiply(1234, 5678));
        System.out.println("Master theorem: T(n)=3T(n/2)+O(n) => O(n^log2 3)");
        System.out.println("Interpretation: one cross product is derived algebraically instead of computed directly.");
    }

    private static void runQuickselect() {
        int[] values = {9, 1, 8, 2, 7, 3, 6};
        System.out.println("\nQuickselect");
        System.out.println("Result: 3rd smallest = " + Quickselect.kthSmallest(values, 2));
        System.out.println("Average recurrence: T(n)=T(n/2)+O(n) => O(n), worst case O(n^2)");
        System.out.println("Interpretation: partitioning discards one side after every pivot decision.");
    }

    private static void runMedianOfMedians() {
        List<Integer> values = List.of(9, 1, 8, 2, 7, 3, 6, 4, 5);
        System.out.println("\nMedian of Medians");
        System.out.println("Result: median = " + MedianOfMedians.kthSmallest(values, values.size() / 2));
        System.out.println("Deterministic selection: O(n) worst-case via guaranteed pivot quality");
        System.out.println("Interpretation: grouping by fives prevents consistently terrible pivots.");
    }

    private static void runBinarySearch() {
        int[] values = {1, 3, 5, 7, 9, 11, 13};
        System.out.println("\nBinary Search");
        System.out.println("Example: find 9 in " + Arrays.toString(values));
        System.out.println("Result: index = " + BinarySearch.indexOf(values, 9));
        System.out.println("Recurrence: T(n)=T(n/2)+O(1) => O(log n)");
    }

    private static void runMergeSort() {
        int[] values = {8, 3, 7, 4, 9, 2, 6, 5};
        System.out.println("\nMerge Sort");
        System.out.println("Example: sort " + Arrays.toString(values));
        System.out.println("Result: " + Arrays.toString(MergeSort.sort(values)));
        System.out.println("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)");
    }

    private static void runCountingInversions() {
        int[] values = {2, 4, 1, 3, 5};
        System.out.println("\nCounting Inversions");
        System.out.println("Example: count inversions in " + Arrays.toString(values));
        System.out.println("Result: inversions = " + CountingInversions.count(values));
        System.out.println("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)");
    }

    private static void runMaximumSubarray() {
        int[] values = {-2, 1, -3, 4, -1, 2, 1, -5, 4};
        System.out.println("\nMaximum Subarray");
        System.out.println("Example: " + Arrays.toString(values));
        System.out.println("Result: " + MaximumSubarray.find(values));
        System.out.println("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)");
    }

    private static void runExponentiation() {
        System.out.println("\nExponentiation by Squaring");
        System.out.println("Example: 3^13");
        System.out.println("Result: " + ExponentiationBySquaring.pow(3, 13));
        System.out.println("Recurrence: T(n)=T(n/2)+O(1) => O(log n)");
    }

    private static void runToomCook() {
        BigInteger left = new BigInteger("123456789012345");
        BigInteger right = new BigInteger("987654321098765");
        System.out.println("\nToom-Cook Multiplication");
        System.out.println("Example: " + left + " * " + right);
        System.out.println("Result: " + ToomCookMultiplication.multiply(left, right));
        System.out.println("Strategy: split into three parts, evaluate, recursively multiply, interpolate.");
    }

    private static void runPolynomial() {
        int[] a = {1, 2, 3};
        int[] b = {4, 5};
        System.out.println("\nPolynomial Multiplication");
        System.out.println("Example: " + Arrays.toString(a) + " * " + Arrays.toString(b));
        System.out.println("Result coefficients: " + Arrays.toString(PolynomialMultiplication.multiply(a, b)));
        System.out.println("Strategy: Karatsuba-style split and combine for coefficient arrays.");
    }

    private static void runSkyline() {
        List<SkylineProblem.Building> buildings = List.of(
                new SkylineProblem.Building(2, 9, 10),
                new SkylineProblem.Building(3, 7, 15),
                new SkylineProblem.Building(5, 12, 12)
        );
        System.out.println("\nSkyline Problem");
        System.out.println("Result key points: " + SkylineProblem.skyline(buildings));
        System.out.println("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)");
    }

    private static void runMajority() {
        int[] values = {2, 2, 1, 2, 3, 2, 2};
        System.out.println("\nMajority Element");
        System.out.println("Example: " + Arrays.toString(values));
        System.out.println("Result: " + MajorityElement.find(values).orElseThrow());
        System.out.println("Strategy: recursively compare majority candidates from both halves.");
    }

    private static void runRotatedSearch() {
        int[] values = {13, 18, 25, 2, 8, 10};
        System.out.println("\nSearch in Rotated Sorted Array");
        System.out.println("Example: find 8 in " + Arrays.toString(values));
        System.out.println("Result: index = " + RotatedArraySearch.indexOf(values, 8));
        System.out.println("Recurrence: T(n)=T(n/2)+O(1) => O(log n)");
    }

    private static void runPeakFinding() {
        int[] values = {1, 3, 7, 12, 9, 5};
        int index = PeakFinding.peakIndex(values);
        System.out.println("\nPeak Finding");
        System.out.println("Example: " + Arrays.toString(values));
        System.out.println("Result: index = " + index + ", value = " + values[index]);
        System.out.println("Recurrence: T(n)=T(n/2)+O(1) => O(log n)");
    }

    private static void runTwoSortedSelection() {
        int[] first = {1, 4, 7, 10};
        int[] second = {2, 3, 6, 8, 9};
        System.out.println("\nSelection in Two Sorted Arrays");
        System.out.println("Result: 5th smallest = " + SelectionInTwoSortedArrays.kthSmallest(first, second, 4));
        System.out.println("Strategy: discard k/2 elements from one sorted array each step.");
    }

    private static void runDivideConquerDp() {
        int[] values = {2, 1, 3, 4, 2};
        System.out.println("\nDivide-and-Conquer DP Optimization");
        System.out.println("Example: partition " + Arrays.toString(values) + " into 2 groups");
        System.out.println("Result: min squared partition cost = "
                + DivideConquerDpOptimization.minSquaredPartitionCost(values, 2));
        System.out.println("Strategy: compute DP rows with monotonic optimal split ranges.");
    }

    private static void runSegmentTree() {
        int[] values = {1, 3, 5, 7, 9, 11};
        SegmentTree tree = new SegmentTree(values);
        System.out.println("\nSegment Tree Construction");
        System.out.println("Example: range sum [1, 3] over " + Arrays.toString(values));
        System.out.println("Result: " + tree.rangeSum(1, 3));
        System.out.println("Strategy: recursively build parent nodes from child intervals.");
    }

    private static void runFftConvolution() {
        double[] result = FastFourierTransform.convolution(new double[]{1, 2, 3}, new double[]{4, 5});
        System.out.println("\nFFT Convolution Variant");
        System.out.println("Example: convolve [1, 2, 3] and [4, 5]");
        System.out.println("Result: " + Arrays.toString(round(result)));
        System.out.println("Strategy: FFT both signals, multiply pointwise, inverse FFT.");
    }

    private static void runBenchmarks(Options options) {
        System.out.println("\nBenchmark Mode");
        System.out.println("Times are single-run educational timings in milliseconds.");
        System.out.println();
        System.out.printf("%-20s %-8s %-12s %-20s%n", "Algorithm", "Size", "Millis", "Result summary");
        for (int size : options.sizes) {
            benchmark("quickselect", size, () -> Quickselect.kthSmallest(shuffled(size), size / 2));
            benchmark("median-of-medians", size, () -> MedianOfMedians.kthSmallest(boxed(shuffled(size)), size / 2));
            benchmark("fft", size, () -> FastFourierTransform.fft(complexSignal(nextPowerOfTwo(size)))[0].real());
            benchmark("closest-pair", size, () -> ClosestPair.find(points(size)).distance());
            benchmark("convex-hull", size, () -> ConvexHull.monotonicChain(hullPoints(size)).size());
            benchmark("merge-sort", size, () -> MergeSort.sort(shuffled(size))[size / 2]);
            benchmark("counting-inversions", size, () -> CountingInversions.count(shuffled(size)));
            benchmark("binary-search", size, () -> BinarySearch.indexOf(IntStream.range(0, size).toArray(), size / 2));
            benchmark("maximum-subarray", size, () -> MaximumSubarray.find(alternatingValues(size)).sum());
            if (size <= 64 && (size & (size - 1)) == 0) {
                benchmark("strassen", size, () -> StrassenMatrix.multiply(matrix(size), matrix(size))[0][0]);
            }
            benchmark("karatsuba", size, () -> Karatsuba.multiply(123_456_789L + size, 987_654_321L - size));
            benchmark("exponentiation", size, () -> ExponentiationBySquaring.pow(2, Math.min(size, 62)));
            benchmark("rotated-search", size, () -> RotatedArraySearch.indexOf(rotated(size), size / 2));
            benchmark("peak-finding", size, () -> PeakFinding.peakIndex(alternatingValues(size)));
        }
    }

    private static void benchmark(String name, int size, BenchmarkTask task) {
        long start = System.nanoTime();
        Object result = task.run();
        long elapsed = System.nanoTime() - start;
        System.out.printf(Locale.US, "%-20s %-8d %-12.3f %-20s%n", name, size, elapsed / 1_000_000.0, summarize(result));
    }

    private static String summarize(Object result) {
        String value = String.valueOf(result);
        return value.length() > 20 ? value.substring(0, 17) + "..." : value;
    }

    private static int[] shuffled(int size) {
        int[] values = IntStream.range(0, size).toArray();
        Random random = new Random(42L + size);
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = values[i];
            values[i] = values[j];
            values[j] = temp;
        }
        return values;
    }

    private static List<Integer> boxed(int[] values) {
        return Arrays.stream(values).boxed().collect(Collectors.toList());
    }

    private static FastFourierTransform.Complex[] complexSignal(int size) {
        FastFourierTransform.Complex[] values = new FastFourierTransform.Complex[size];
        for (int i = 0; i < size; i++) {
            values[i] = new FastFourierTransform.Complex(Math.sin(i), 0);
        }
        return values;
    }

    private static int nextPowerOfTwo(int size) {
        int result = 1;
        while (result < size) {
            result *= 2;
        }
        return result;
    }

    private static List<ClosestPair.Point> points(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> new ClosestPair.Point((i * 37) % 997, (i * 91) % 991))
                .collect(Collectors.toList());
    }

    private static List<ConvexHull.Point> hullPoints(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> new ConvexHull.Point(Math.cos(i) * 100 + i % 7, Math.sin(i) * 100 - i % 5))
                .collect(Collectors.toList());
    }

    private static int[][] matrix(int size) {
        int[][] values = new int[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                values[row][col] = (row + col) % 10;
            }
        }
        return values;
    }

    private static int[] alternatingValues(int size) {
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = i % 3 == 0 ? -i : i;
        }
        return values;
    }

    private static int[] rotated(int size) {
        int[] values = new int[size];
        int pivot = Math.max(1, size / 3);
        for (int i = 0; i < size; i++) {
            values[i] = (i + pivot) % size;
        }
        return values;
    }

    private static double[] round(double[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = Math.round(values[i] * 1000.0) / 1000.0;
        }
        return result;
    }

    private static void printTrace(String algorithm) {
        if ("all".equals(algorithm) || "karatsuba".equals(algorithm)) {
            System.out.println("\nKaratsuba Trace");
            System.out.println("1234 x 5678");
            System.out.println("|-- split: 12|34 and 56|78");
            System.out.println("|-- z2 = 12 x 56");
            System.out.println("|-- z0 = 34 x 78");
            System.out.println("|-- z1 = (12+34) x (56+78) - z2 - z0");
            System.out.println("`-- combine: z2*10^4 + z1*10^2 + z0 = 7006652");
        }
        if ("all".equals(algorithm) || "strassen".equals(algorithm)) {
            System.out.println("\nStrassen Trace");
            System.out.println("C = A x B");
            System.out.println("|-- split A and B into quadrants");
            System.out.println("|-- compute P1..P7 recursive products");
            System.out.println("|-- C11 = P1 + P4 - P5 + P7");
            System.out.println("|-- C12 = P3 + P5");
            System.out.println("|-- C21 = P2 + P4");
            System.out.println("`-- C22 = P1 + P3 - P2 + P6");
        }
        if ("all".equals(algorithm) || "merge-sort".equals(algorithm)) {
            System.out.println("\nMerge Sort Trace");
            System.out.println("[8, 3, 7, 4]");
            System.out.println("|-- split into [8, 3] and [7, 4]");
            System.out.println("|-- recursively sort both halves");
            System.out.println("`-- merge sorted halves into [3, 4, 7, 8]");
        }
        if ("all".equals(algorithm) || "exponentiation".equals(algorithm)) {
            System.out.println("\nExponentiation Trace");
            System.out.println("3^13");
            System.out.println("|-- 3^6 squared, then multiply by 3 because exponent is odd");
            System.out.println("|-- 3^6 uses 3^3 squared");
            System.out.println("`-- stop at exponent 0, then combine while unwinding");
        }
    }

    @FunctionalInterface
    private interface BenchmarkTask {
        Object run();
    }

    private static final class Options {
        private final String algorithm;
        private final boolean benchmark;
        private final boolean trace;
        private final boolean help;
        private final int[] sizes;

        private Options(String algorithm, boolean benchmark, boolean trace, boolean help, int[] sizes) {
            this.algorithm = algorithm;
            this.benchmark = benchmark;
            this.trace = trace;
            this.help = help;
            this.sizes = sizes;
        }

        private static Options parse(String[] args) {
            String algorithm = "all";
            boolean benchmark = false;
            boolean trace = false;
            boolean help = false;
            int[] sizes = {16, 64, 256, 1024};

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--algorithm":
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--algorithm requires a value");
                        }
                        algorithm = args[++i];
                        break;
                    case "--benchmark":
                        benchmark = true;
                        break;
                    case "--trace":
                        trace = true;
                        break;
                    case "--sizes":
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--sizes requires a comma-separated value");
                        }
                        StringBuilder rawSizes = new StringBuilder(args[++i]);
                        while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                            rawSizes.append(',').append(args[++i]);
                        }
                        sizes = parseSizes(rawSizes.toString());
                        break;
                    case "--help":
                        help = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            validateAlgorithm(algorithm);
            if (sizes.length == 0) {
                throw new IllegalArgumentException("--sizes must contain at least one positive integer");
            }
            return new Options(algorithm, benchmark, trace, help, sizes);
        }

        private static void validateAlgorithm(String algorithm) {
            List<String> supported = List.of(
                    "all",
                    "strassen",
                    "closest-pair",
                    "convex-hull",
                    "fft",
                    "karatsuba",
                    "quickselect",
                    "median-of-medians",
                    "binary-search",
                    "merge-sort",
                    "counting-inversions",
                    "maximum-subarray",
                    "exponentiation",
                    "toom-cook",
                    "polynomial",
                    "skyline",
                    "majority",
                    "rotated-search",
                    "peak-finding",
                    "two-sorted-selection",
                    "dc-dp",
                    "segment-tree",
                    "fft-convolution"
            );
            if (!supported.contains(algorithm)) {
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
            }
        }

        private static int[] parseSizes(String raw) {
            try {
                int[] parsed = Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .mapToInt(Integer::parseInt)
                        .filter(value -> value > 0)
                        .toArray();
                if (parsed.length == 0) {
                    throw new IllegalArgumentException("--sizes must contain at least one positive integer");
                }
                return parsed;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("--sizes must contain only positive integers", ex);
            }
        }
    }
}
