"""Console demo for the Python implementations."""

from __future__ import annotations

import argparse
import math
import random
import time
from collections.abc import Callable

from divide_conquer.algorithms import (
    binary_search,
    closest_pair,
    convex_hull,
    counting_inversions,
    dc_dp_optimization,
    exponentiation_by_squaring,
    fft,
    karatsuba,
    majority_element,
    maximum_subarray,
    median_of_medians,
    merge_sort,
    peak_finding,
    polynomial_multiplication,
    quickselect,
    rotated_array_search,
    segment_tree,
    skyline,
    strassen_matrix,
    toom_cook,
    two_sorted_selection,
)

AlgorithmRunner = Callable[[], None]


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    print("DIVIDE-CONQUER Python Demo")
    print("==========================")
    if args.benchmark:
        run_benchmarks(args.sizes)
        return
    run_selected(args.algorithm)
    if args.trace:
        print_trace(args.algorithm)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Educational divide-and-conquer algorithm demos.")
    parser.add_argument(
        "--algorithm",
        default="all",
        choices=[
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
            "fft-convolution",
        ],
        help="Run one algorithm demo.",
    )
    parser.add_argument(
        "--benchmark",
        action="store_true",
        help="Print timing tables for deterministic generated inputs.",
    )
    parser.add_argument("--trace", action="store_true", help="Print visual recursive split/combine traces.")
    parser.add_argument(
        "--sizes",
        nargs="+",
        default=["16,64,256,1024"],
        help="Comma-separated benchmark sizes. Default: 16,64,256,1024.",
    )
    args = parser.parse_args(argv)
    raw_sizes = ",".join(args.sizes)
    try:
        args.sizes = [int(value.strip()) for value in raw_sizes.split(",") if value.strip()]
    except ValueError:
        parser.error("--sizes must contain only positive integers")
    if not args.sizes or any(size <= 0 for size in args.sizes):
        parser.error("--sizes must contain positive integers")
    return args


def run_selected(algorithm: str) -> None:
    runners: dict[str, AlgorithmRunner] = {
        "strassen": run_strassen,
        "closest-pair": run_closest_pair,
        "convex-hull": run_convex_hull,
        "fft": run_fft,
        "karatsuba": run_karatsuba,
        "quickselect": run_quickselect,
        "median-of-medians": run_median_of_medians,
        "binary-search": run_binary_search,
        "merge-sort": run_merge_sort,
        "counting-inversions": run_counting_inversions,
        "maximum-subarray": run_maximum_subarray,
        "exponentiation": run_exponentiation,
        "toom-cook": run_toom_cook,
        "polynomial": run_polynomial,
        "skyline": run_skyline,
        "majority": run_majority,
        "rotated-search": run_rotated_search,
        "peak-finding": run_peak_finding,
        "two-sorted-selection": run_two_sorted_selection,
        "dc-dp": run_divide_conquer_dp,
        "segment-tree": run_segment_tree,
        "fft-convolution": run_fft_convolution,
    }
    selected = runners.values() if algorithm == "all" else [runners[algorithm]]
    for runner in selected:
        runner()


def run_strassen() -> None:
    result = strassen_matrix.multiply([[1, 2], [3, 4]], [[5, 6], [7, 8]])
    print("\nStrassen Matrix Multiplication")
    print(f"Result: {result}")
    print("Master theorem: T(n)=7T(n/2)+O(n^2) => O(n^log2 7)")
    print("Interpretation: seven recursive half-size products beat the eight products of classical splitting.")


def run_closest_pair() -> None:
    points = [
        closest_pair.Point(0, 0),
        closest_pair.Point(5, 4),
        closest_pair.Point(3, 1),
        closest_pair.Point(9, 6),
        closest_pair.Point(3, 2),
    ]
    result = closest_pair.find(points)
    print("\nClosest Pair")
    print(f"Result: {result.first} <-> {result.second}, distance {result.distance:.3f}")
    print("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)")
    print("Interpretation: sorted-by-y strip merging keeps the combine step linear.")


def run_convex_hull() -> None:
    points = [
        convex_hull.Point(0, 0),
        convex_hull.Point(1, 1),
        convex_hull.Point(2, 0),
        convex_hull.Point(2, 2),
        convex_hull.Point(0, 2),
    ]
    print("\nConvex Hull")
    print(f"Result: {convex_hull.monotonic_chain(points)}")
    print("Strategy: sort then build lower/upper hulls => O(n log n)")
    print("Interpretation: sorting dominates; each point is pushed and popped at most once.")


def run_fft() -> None:
    result = fft.fft([1 + 0j, 2 + 0j, 3 + 0j, 4 + 0j])
    rounded = [complex(round(value.real, 2), round(value.imag, 2)) for value in result]
    print("\nFast Fourier Transform")
    print(f"Result: {rounded}")
    print("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)")
    print("Interpretation: even/odd decomposition leaves linear twiddle-factor combine work.")


def run_karatsuba() -> None:
    print("\nKaratsuba Multiplication")
    print(f"Result: 1234 * 5678 = {karatsuba.multiply(1234, 5678)}")
    print("Master theorem: T(n)=3T(n/2)+O(n) => O(n^log2 3)")
    print("Interpretation: one cross product is derived algebraically instead of computed directly.")


def run_quickselect() -> None:
    values = [9, 1, 8, 2, 7, 3, 6]
    print("\nQuickselect")
    print(f"Result: 3rd smallest = {quickselect.kth_smallest(values, 2)}")
    print("Average recurrence: T(n)=T(n/2)+O(n) => O(n), worst case O(n^2)")
    print("Interpretation: partitioning discards one side after every pivot decision.")


def run_median_of_medians() -> None:
    values = [9, 1, 8, 2, 7, 3, 6, 4, 5]
    print("\nMedian of Medians")
    print(f"Result: median = {median_of_medians.kth_smallest(values, len(values) // 2)}")
    print("Deterministic selection: O(n) worst-case via guaranteed pivot quality")
    print("Interpretation: grouping by fives prevents consistently terrible pivots.")


def run_binary_search() -> None:
    values = [1, 3, 5, 7, 9, 11, 13]
    print("\nBinary Search")
    print(f"Example: find 9 in {values}")
    print(f"Result: index = {binary_search.index_of(values, 9)}")
    print("Recurrence: T(n)=T(n/2)+O(1) => O(log n)")


def run_merge_sort() -> None:
    values = [8, 3, 7, 4, 9, 2, 6, 5]
    print("\nMerge Sort")
    print(f"Example: sort {values}")
    print(f"Result: {merge_sort.sort(values)}")
    print("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)")


def run_counting_inversions() -> None:
    values = [2, 4, 1, 3, 5]
    print("\nCounting Inversions")
    print(f"Example: count inversions in {values}")
    print(f"Result: inversions = {counting_inversions.count(values)}")
    print("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)")


def run_maximum_subarray() -> None:
    values = [-2, 1, -3, 4, -1, 2, 1, -5, 4]
    print("\nMaximum Subarray")
    print(f"Example: {values}")
    print(f"Result: {maximum_subarray.find(values)}")
    print("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)")


def run_exponentiation() -> None:
    print("\nExponentiation by Squaring")
    print("Example: 3^13")
    print(f"Result: {exponentiation_by_squaring.pow_int(3, 13)}")
    print("Recurrence: T(n)=T(n/2)+O(1) => O(log n)")


def run_toom_cook() -> None:
    left = 123_456_789_012_345
    right = 987_654_321_098_765
    print("\nToom-Cook Multiplication")
    print(f"Example: {left} * {right}")
    print(f"Result: {toom_cook.multiply(left, right)}")
    print("Strategy: split into three parts, evaluate, recursively multiply, interpolate.")


def run_polynomial() -> None:
    first = [1, 2, 3]
    second = [4, 5]
    print("\nPolynomial Multiplication")
    print(f"Example: {first} * {second}")
    print(f"Result coefficients: {polynomial_multiplication.multiply(first, second)}")
    print("Strategy: Karatsuba-style split and combine for coefficient arrays.")


def run_skyline() -> None:
    buildings = [
        skyline.Building(2, 9, 10),
        skyline.Building(3, 7, 15),
        skyline.Building(5, 12, 12),
    ]
    print("\nSkyline Problem")
    print(f"Result key points: {skyline.skyline(buildings)}")
    print("Master theorem: T(n)=2T(n/2)+O(n) => O(n log n)")


def run_majority() -> None:
    values = [2, 2, 1, 2, 3, 2, 2]
    print("\nMajority Element")
    print(f"Example: {values}")
    print(f"Result: {majority_element.find(values)}")
    print("Strategy: recursively compare majority candidates from both halves.")


def run_rotated_search() -> None:
    values = [13, 18, 25, 2, 8, 10]
    print("\nSearch in Rotated Sorted Array")
    print(f"Example: find 8 in {values}")
    print(f"Result: index = {rotated_array_search.index_of(values, 8)}")
    print("Recurrence: T(n)=T(n/2)+O(1) => O(log n)")


def run_peak_finding() -> None:
    values = [1, 3, 7, 12, 9, 5]
    index = peak_finding.peak_index(values)
    print("\nPeak Finding")
    print(f"Example: {values}")
    print(f"Result: index = {index}, value = {values[index]}")
    print("Recurrence: T(n)=T(n/2)+O(1) => O(log n)")


def run_two_sorted_selection() -> None:
    first = [1, 4, 7, 10]
    second = [2, 3, 6, 8, 9]
    print("\nSelection in Two Sorted Arrays")
    print(f"Result: 5th smallest = {two_sorted_selection.kth_smallest(first, second, 4)}")
    print("Strategy: discard k/2 elements from one sorted array each step.")


def run_divide_conquer_dp() -> None:
    values = [2, 1, 3, 4, 2]
    print("\nDivide-and-Conquer DP Optimization")
    print(f"Example: partition {values} into 2 groups")
    print(f"Result: min squared partition cost = {dc_dp_optimization.min_squared_partition_cost(values, 2)}")
    print("Strategy: compute DP rows with monotonic optimal split ranges.")


def run_segment_tree() -> None:
    values = [1, 3, 5, 7, 9, 11]
    tree = segment_tree.SegmentTree(values)
    print("\nSegment Tree Construction")
    print(f"Example: range sum [1, 3] over {values}")
    print(f"Result: {tree.range_sum(1, 3)}")
    print("Strategy: recursively build parent nodes from child intervals.")


def run_fft_convolution() -> None:
    result = fft.convolution([1, 2, 3], [4, 5])
    rounded = [round(value, 3) for value in result]
    print("\nFFT Convolution Variant")
    print("Example: convolve [1, 2, 3] and [4, 5]")
    print(f"Result: {rounded}")
    print("Strategy: FFT both signals, multiply pointwise, inverse FFT.")


def run_benchmarks(sizes: list[int]) -> None:
    print("\nBenchmark Mode")
    print("Times are single-run educational timings in milliseconds.\n")
    print(f"{'Algorithm':<20} {'Size':<8} {'Millis':<12} {'Result summary':<20}")
    for size in sizes:
        benchmark("quickselect", size, lambda size=size: quickselect.kth_smallest(shuffled(size), size // 2))
        benchmark(
            "median-of-medians",
            size,
            lambda size=size: median_of_medians.kth_smallest(shuffled(size), size // 2),
        )
        benchmark("fft", size, lambda size=size: fft.fft(complex_signal(next_power_of_two(size)))[0].real)
        benchmark("closest-pair", size, lambda size=size: closest_pair.find(points(size)).distance)
        benchmark("convex-hull", size, lambda size=size: len(convex_hull.monotonic_chain(hull_points(size))))
        benchmark("merge-sort", size, lambda size=size: merge_sort.sort(shuffled(size))[size // 2])
        benchmark("counting-inversions", size, lambda size=size: counting_inversions.count(shuffled(size)))
        benchmark("binary-search", size, lambda size=size: binary_search.index_of(list(range(size)), size // 2))
        benchmark("maximum-subarray", size, lambda size=size: maximum_subarray.find(alternating_values(size)).total)
        if size <= 64 and size & (size - 1) == 0:
            benchmark("strassen", size, lambda size=size: strassen_matrix.multiply(matrix(size), matrix(size))[0][0])
        benchmark("karatsuba", size, lambda size=size: karatsuba.multiply(123_456_789 + size, 987_654_321 - size))
        benchmark("exponentiation", size, lambda size=size: exponentiation_by_squaring.pow_int(2, min(size, 62)))
        benchmark("rotated-search", size, lambda size=size: rotated_array_search.index_of(rotated(size), size // 2))
        benchmark("peak-finding", size, lambda size=size: peak_finding.peak_index(alternating_values(size)))


def benchmark(name: str, size: int, task: Callable[[], object]) -> None:
    start = time.perf_counter()
    result = task()
    elapsed = (time.perf_counter() - start) * 1000
    print(f"{name:<20} {size:<8} {elapsed:<12.3f} {summarize(result):<20}")


def summarize(result: object) -> str:
    value = str(result)
    return value if len(value) <= 20 else f"{value[:17]}..."


def shuffled(size: int) -> list[int]:
    values = list(range(size))
    random.Random(42 + size).shuffle(values)
    return values


def complex_signal(size: int) -> list[complex]:
    return [complex(math.sin(index), 0) for index in range(size)]


def next_power_of_two(size: int) -> int:
    result = 1
    while result < size:
        result *= 2
    return result


def points(size: int) -> list[closest_pair.Point]:
    return [closest_pair.Point((index * 37) % 997, (index * 91) % 991) for index in range(size)]


def hull_points(size: int) -> list[convex_hull.Point]:
    return [
        convex_hull.Point(math.cos(index) * 100 + index % 7, math.sin(index) * 100 - index % 5)
        for index in range(size)
    ]


def matrix(size: int) -> list[list[int]]:
    return [[(row + col) % 10 for col in range(size)] for row in range(size)]


def alternating_values(size: int) -> list[int]:
    return [-index if index % 3 == 0 else index for index in range(size)]


def rotated(size: int) -> list[int]:
    pivot = max(1, size // 3)
    return [(index + pivot) % size for index in range(size)]


def print_trace(algorithm: str) -> None:
    if algorithm in {"all", "karatsuba"}:
        print("\nKaratsuba Trace")
        print("1234 x 5678")
        print("|-- split: 12|34 and 56|78")
        print("|-- z2 = 12 x 56")
        print("|-- z0 = 34 x 78")
        print("|-- z1 = (12+34) x (56+78) - z2 - z0")
        print("`-- combine: z2*10^4 + z1*10^2 + z0 = 7006652")
    if algorithm in {"all", "strassen"}:
        print("\nStrassen Trace")
        print("C = A x B")
        print("|-- split A and B into quadrants")
        print("|-- compute P1..P7 recursive products")
        print("|-- C11 = P1 + P4 - P5 + P7")
        print("|-- C12 = P3 + P5")
        print("|-- C21 = P2 + P4")
        print("`-- C22 = P1 + P3 - P2 + P6")
    if algorithm in {"all", "merge-sort"}:
        print("\nMerge Sort Trace")
        print("[8, 3, 7, 4]")
        print("|-- split into [8, 3] and [7, 4]")
        print("|-- recursively sort both halves")
        print("`-- merge sorted halves into [3, 4, 7, 8]")
    if algorithm in {"all", "exponentiation"}:
        print("\nExponentiation Trace")
        print("3^13")
        print("|-- 3^6 squared, then multiply by 3 because exponent is odd")
        print("|-- 3^6 uses 3^3 squared")
        print("`-- stop at exponent 0, then combine while unwinding")


if __name__ == "__main__":
    main()
