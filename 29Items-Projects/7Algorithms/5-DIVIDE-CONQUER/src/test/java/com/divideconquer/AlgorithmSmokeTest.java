package com.divideconquer;

import com.divideconquer.app.DemoRunner;
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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlgorithmSmokeTest {
    @Test
    void strassenMultipliesTwoByTwoMatrices() {
        int[][] result = StrassenMatrix.multiply(new int[][]{{1, 2}, {3, 4}}, new int[][]{{5, 6}, {7, 8}});
        assertArrayEquals(new int[][]{{19, 22}, {43, 50}}, result);
    }

    @Test
    void karatsubaMultipliesIntegers() {
        assertEquals(7_006_652, Karatsuba.multiply(1234, 5678));
    }

    @Test
    void quickselectFindsKthSmallest() {
        assertEquals(3, Quickselect.kthSmallest(new int[]{9, 1, 8, 2, 7, 3, 6}, 2));
    }

    @Test
    void medianOfMediansFindsMedian() {
        assertEquals(5, MedianOfMedians.kthSmallest(List.of(9, 1, 8, 2, 7, 3, 6, 4, 5), 4));
    }

    @Test
    void closestPairFindsShortDistance() {
        ClosestPair.Result result = ClosestPair.find(List.of(
                new ClosestPair.Point(0, 0),
                new ClosestPair.Point(3, 1),
                new ClosestPair.Point(3, 2)
        ));
        assertEquals(1.0, result.distance(), 0.0001);
    }

    @Test
    void convexHullExcludesInteriorPoint() {
        List<ConvexHull.Point> hull = ConvexHull.monotonicChain(List.of(
                new ConvexHull.Point(0, 0),
                new ConvexHull.Point(1, 1),
                new ConvexHull.Point(2, 0),
                new ConvexHull.Point(2, 2),
                new ConvexHull.Point(0, 2)
        ));
        assertEquals(4, hull.size());
    }

    @Test
    void fftTransformsFourValues() {
        FastFourierTransform.Complex[] result = FastFourierTransform.fft(new FastFourierTransform.Complex[]{
                new FastFourierTransform.Complex(1, 0),
                new FastFourierTransform.Complex(2, 0),
                new FastFourierTransform.Complex(3, 0),
                new FastFourierTransform.Complex(4, 0)
        });
        assertEquals(10.0, result[0].real(), 0.0001);
    }

    @Test
    void binarySearchFindsPresentAndMissingValues() {
        int[] values = {1, 3, 5, 7, 9, 11, 13};
        assertEquals(4, BinarySearch.indexOf(values, 9));
        assertEquals(-1, BinarySearch.indexOf(values, 10));
    }

    @Test
    void mergeSortReturnsSortedCopy() {
        int[] values = {8, 3, 7, 4, 9, 2, 6, 5};
        assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7, 8, 9}, MergeSort.sort(values));
        assertArrayEquals(new int[]{8, 3, 7, 4, 9, 2, 6, 5}, values);
    }

    @Test
    void countingInversionsCountsSplitInversions() {
        assertEquals(3, CountingInversions.count(new int[]{2, 4, 1, 3, 5}));
        assertEquals(10, CountingInversions.count(new int[]{5, 4, 3, 2, 1}));
    }

    @Test
    void maximumSubarrayFindsBestCrossingRange() {
        MaximumSubarray.Result result = MaximumSubarray.find(new int[]{-2, 1, -3, 4, -1, 2, 1, -5, 4});
        assertEquals(3, result.startInclusive());
        assertEquals(6, result.endInclusive());
        assertEquals(6, result.sum());
    }

    @Test
    void exponentiationBySquaringComputesPowers() {
        assertEquals(1, ExponentiationBySquaring.pow(5, 0));
        assertEquals(1_594_323, ExponentiationBySquaring.pow(3, 13));
    }

    @Test
    void toomCookMatchesBigIntegerMultiplication() {
        BigInteger left = new BigInteger("123456789012345");
        BigInteger right = new BigInteger("987654321098765");
        assertEquals(left.multiply(right), ToomCookMultiplication.multiply(left, right));
    }

    @Test
    void polynomialMultiplicationCombinesCoefficients() {
        assertArrayEquals(new int[]{4, 13, 22, 15}, PolynomialMultiplication.multiply(new int[]{1, 2, 3}, new int[]{4, 5}));
    }

    @Test
    void skylineMergesBuildingOutlines() {
        List<SkylineProblem.KeyPoint> result = SkylineProblem.skyline(List.of(
                new SkylineProblem.Building(2, 9, 10),
                new SkylineProblem.Building(3, 7, 15),
                new SkylineProblem.Building(5, 12, 12)
        ));
        assertEquals("(2, 10)", result.get(0).toString());
        assertEquals("(3, 15)", result.get(1).toString());
        assertEquals("(12, 0)", result.get(result.size() - 1).toString());
    }

    @Test
    void majorityElementFindsOnlyStrictMajority() {
        assertEquals(2, MajorityElement.find(new int[]{2, 2, 1, 2, 3, 2, 2}).orElseThrow());
        assertEquals(false, MajorityElement.find(new int[]{1, 2, 3, 4}).isPresent());
    }

    @Test
    void rotatedSearchAndPeakFindingWork() {
        assertEquals(4, RotatedArraySearch.indexOf(new int[]{13, 18, 25, 2, 8, 10}, 8));
        int peak = PeakFinding.peakIndex(new int[]{1, 3, 7, 12, 9, 5});
        assertEquals(12, new int[]{1, 3, 7, 12, 9, 5}[peak]);
    }

    @Test
    void selectionInTwoSortedArraysFindsKthValue() {
        assertEquals(7, SelectionInTwoSortedArrays.kthSmallest(new int[]{1, 4, 7, 10}, new int[]{2, 3, 6, 8, 9}, 5));
    }

    @Test
    void divideConquerDpOptimizationComputesPartitionCost() {
        assertEquals(72, DivideConquerDpOptimization.minSquaredPartitionCost(new int[]{2, 1, 3, 4, 2}, 2));
    }

    @Test
    void segmentTreeAnswersRangeSumQueries() {
        SegmentTree tree = new SegmentTree(new int[]{1, 3, 5, 7, 9, 11});
        assertEquals(15, tree.rangeSum(1, 3));
    }

    @Test
    void fftConvolutionMultipliesSignals() {
        double[] result = FastFourierTransform.convolution(new double[]{1, 2, 3}, new double[]{4, 5});
        assertEquals(4.0, result[0], 0.0001);
        assertEquals(13.0, result[1], 0.0001);
        assertEquals(22.0, result[2], 0.0001);
        assertEquals(15.0, result[3], 0.0001);
    }

    @Test
    void randomizedSelectionMatchesSortedBaseline() {
        Random random = new Random(123);
        for (int round = 0; round < 50; round++) {
            int[] values = new int[31];
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextInt(200) - 100;
            }
            int k = random.nextInt(values.length);
            int[] sorted = Arrays.copyOf(values, values.length);
            Arrays.sort(sorted);

            assertEquals(sorted[k], Quickselect.kthSmallest(values, k));
            List<Integer> boxed = new ArrayList<>();
            for (int value : values) {
                boxed.add(value);
            }
            assertEquals(sorted[k], MedianOfMedians.kthSmallest(boxed, k));
        }
    }

    @Test
    void strassenMatchesClassicalBaselineForFourByFourMatrices() {
        int[][] a = {
                {1, 2, 3, 4},
                {5, 6, 7, 8},
                {9, 1, 2, 3},
                {4, 5, 6, 7}
        };
        int[][] b = {
                {7, 6, 5, 4},
                {3, 2, 1, 0},
                {1, 3, 5, 7},
                {2, 4, 6, 8}
        };
        assertArrayEquals(classicalMultiply(a, b), StrassenMatrix.multiply(a, b));
    }

    @Test
    void strassenPadsNonPowerOfTwoSquareMatrices() {
        int[][] a = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9}
        };
        int[][] b = {
                {9, 8, 7},
                {6, 5, 4},
                {3, 2, 1}
        };
        assertArrayEquals(classicalMultiply(a, b), StrassenMatrix.multiply(a, b));
    }

    @Test
    void degenerateGeometryCasesAreHandled() {
        List<ConvexHull.Point> collinearHull = ConvexHull.monotonicChain(List.of(
                new ConvexHull.Point(0, 0),
                new ConvexHull.Point(1, 1),
                new ConvexHull.Point(2, 2),
                new ConvexHull.Point(2, 2)
        ));
        assertEquals(2, collinearHull.size());

        ClosestPair.Result duplicateDistance = ClosestPair.find(List.of(
                new ClosestPair.Point(1, 1),
                new ClosestPair.Point(1, 1),
                new ClosestPair.Point(5, 5)
        ));
        assertEquals(0.0, duplicateDistance.distance(), 0.0001);
    }

    @Test
    void invalidInputsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> Quickselect.kthSmallest(new int[]{}, 0));
        assertThrows(IllegalArgumentException.class, () -> MedianOfMedians.kthSmallest(List.of(1, 2), 4));
        assertThrows(IllegalArgumentException.class, () -> FastFourierTransform.fft(new FastFourierTransform.Complex[]{
                new FastFourierTransform.Complex(1, 0),
                new FastFourierTransform.Complex(2, 0),
                new FastFourierTransform.Complex(3, 0)
        }));
        assertThrows(IllegalArgumentException.class, () -> StrassenMatrix.multiply(
                new int[][]{{1, 2, 3}, {4, 5, 6}},
                new int[][]{{1, 2}, {3, 4}}
        ));
        assertThrows(IllegalArgumentException.class, () -> ClosestPair.find(Arrays.asList(
                new ClosestPair.Point(0, 0),
                null
        )));
        assertThrows(IllegalArgumentException.class, () -> FastFourierTransform.fft(new FastFourierTransform.Complex[]{
                new FastFourierTransform.Complex(1, 0),
                null
        }));
        assertThrows(IllegalArgumentException.class, () -> BinarySearch.indexOf(null, 1));
        assertThrows(IllegalArgumentException.class, () -> MergeSort.sort(null));
        assertThrows(IllegalArgumentException.class, () -> CountingInversions.count(null));
        assertThrows(IllegalArgumentException.class, () -> MaximumSubarray.find(new int[]{}));
        assertThrows(IllegalArgumentException.class, () -> ExponentiationBySquaring.pow(2, -1));
        assertThrows(IllegalArgumentException.class, () -> ToomCookMultiplication.multiply(null, BigInteger.ONE));
        assertThrows(IllegalArgumentException.class, () -> PolynomialMultiplication.multiply(new int[]{}, new int[]{1}));
        assertThrows(IllegalArgumentException.class, () -> SkylineProblem.skyline(null));
        assertThrows(IllegalArgumentException.class, () -> MajorityElement.find(new int[]{}));
        assertThrows(IllegalArgumentException.class, () -> RotatedArraySearch.indexOf(null, 1));
        assertThrows(IllegalArgumentException.class, () -> PeakFinding.peakIndex(new int[]{}));
        assertThrows(IllegalArgumentException.class, () -> SelectionInTwoSortedArrays.kthSmallest(new int[]{}, new int[]{}, 0));
        assertThrows(IllegalArgumentException.class, () -> DivideConquerDpOptimization.minSquaredPartitionCost(new int[]{}, 1));
        assertThrows(IllegalArgumentException.class, () -> new SegmentTree(new int[]{}));
    }

    @Test
    void demoRunnerSupportsTraceMode() {
        CapturedRun captured = captureDemoOutput("--algorithm", "merge-sort", "--trace");
        assertEquals(0, captured.exitCode);
        assertEquals(true, captured.stdout.contains("Merge Sort Trace"));
        assertEquals("", captured.stderr);
    }

    @Test
    void demoRunnerRejectsInvalidArgumentsCleanly() {
        CapturedRun captured = captureDemoOutput("--algorithm", "unknown");
        assertEquals(2, captured.exitCode);
        assertEquals(true, captured.stderr.contains("Unknown algorithm"));
    }

    private static CapturedRun captureDemoOutput(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            int exitCode = DemoRunner.run(args);
            return new CapturedRun(exitCode, stdout.toString(), stderr.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static final class CapturedRun {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private CapturedRun(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    private static int[][] classicalMultiply(int[][] a, int[][] b) {
        int[][] result = new int[a.length][b[0].length];
        for (int row = 0; row < a.length; row++) {
            for (int col = 0; col < b[0].length; col++) {
                for (int inner = 0; inner < b.length; inner++) {
                    result[row][col] += a[row][inner] * b[inner][col];
                }
            }
        }
        return result;
    }
}
