import random

import pytest
from divide_conquer import demo
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


def test_strassen_multiplies_two_by_two_matrices() -> None:
    assert strassen_matrix.multiply([[1, 2], [3, 4]], [[5, 6], [7, 8]]) == [[19, 22], [43, 50]]


def test_karatsuba_multiplies_integers() -> None:
    assert karatsuba.multiply(1234, 5678) == 7_006_652
    assert karatsuba.multiply(-12, 34) == -408


def test_quickselect_finds_kth_smallest() -> None:
    assert quickselect.kth_smallest([9, 1, 8, 2, 7, 3, 6], 2) == 3


def test_median_of_medians_finds_median() -> None:
    values = [9, 1, 8, 2, 7, 3, 6, 4, 5]
    assert median_of_medians.kth_smallest(values, len(values) // 2) == 5


def test_closest_pair_finds_short_distance() -> None:
    result = closest_pair.find([
        closest_pair.Point(0, 0),
        closest_pair.Point(3, 1),
        closest_pair.Point(3, 2),
    ])
    assert result.distance == 1.0


def test_closest_pair_uses_recursive_strip_for_larger_inputs() -> None:
    result = closest_pair.find([
        closest_pair.Point(0, 0),
        closest_pair.Point(10, 10),
        closest_pair.Point(20, 20),
        closest_pair.Point(5, 5),
        closest_pair.Point(5, 6),
        closest_pair.Point(30, 30),
    ])
    assert result.distance == 1.0


def test_convex_hull_excludes_interior_point() -> None:
    hull = convex_hull.monotonic_chain([
        convex_hull.Point(0, 0),
        convex_hull.Point(1, 1),
        convex_hull.Point(2, 0),
        convex_hull.Point(2, 2),
        convex_hull.Point(0, 2),
    ])
    assert len(hull) == 4


def test_convex_hull_handles_empty_input() -> None:
    assert convex_hull.monotonic_chain([]) == []


def test_fft_transforms_four_values() -> None:
    result = fft.fft([1 + 0j, 2 + 0j, 3 + 0j, 4 + 0j])
    assert round(result[0].real, 6) == 10.0


def test_binary_search_finds_present_and_missing_values() -> None:
    values = [1, 3, 5, 7, 9, 11, 13]
    assert binary_search.index_of(values, 9) == 4
    assert binary_search.index_of(values, 10) == -1


def test_merge_sort_returns_sorted_copy() -> None:
    values = [8, 3, 7, 4, 9, 2, 6, 5]
    assert merge_sort.sort(values) == [2, 3, 4, 5, 6, 7, 8, 9]
    assert values == [8, 3, 7, 4, 9, 2, 6, 5]


def test_counting_inversions_counts_split_inversions() -> None:
    assert counting_inversions.count([2, 4, 1, 3, 5]) == 3
    assert counting_inversions.count([5, 4, 3, 2, 1]) == 10


def test_maximum_subarray_finds_best_crossing_range() -> None:
    result = maximum_subarray.find([-2, 1, -3, 4, -1, 2, 1, -5, 4])
    assert result == maximum_subarray.Result(start=3, end=6, total=6)


def test_exponentiation_by_squaring_computes_powers() -> None:
    assert exponentiation_by_squaring.pow_int(5, 0) == 1
    assert exponentiation_by_squaring.pow_int(3, 13) == 1_594_323


def test_toom_cook_matches_builtin_multiplication() -> None:
    left = 123_456_789_012_345
    right = 987_654_321_098_765
    assert toom_cook.multiply(left, right) == left * right


def test_polynomial_multiplication_combines_coefficients() -> None:
    assert polynomial_multiplication.multiply([1, 2, 3], [4, 5]) == [4, 13, 22, 15]


def test_skyline_merges_building_outlines() -> None:
    result = skyline.skyline([
        skyline.Building(2, 9, 10),
        skyline.Building(3, 7, 15),
        skyline.Building(5, 12, 12),
    ])
    assert result[0] == skyline.KeyPoint(2, 10)
    assert result[1] == skyline.KeyPoint(3, 15)
    assert result[-1] == skyline.KeyPoint(12, 0)


def test_majority_element_finds_only_strict_majority() -> None:
    assert majority_element.find([2, 2, 1, 2, 3, 2, 2]) == 2
    assert majority_element.find([1, 2, 3, 4]) is None


def test_rotated_search_and_peak_finding_work() -> None:
    assert rotated_array_search.index_of([13, 18, 25, 2, 8, 10], 8) == 4
    values = [1, 3, 7, 12, 9, 5]
    peak = peak_finding.peak_index(values)
    assert values[peak] == 12


def test_selection_in_two_sorted_arrays_finds_kth_value() -> None:
    assert two_sorted_selection.kth_smallest([1, 4, 7, 10], [2, 3, 6, 8, 9], 5) == 7


def test_divide_conquer_dp_optimization_computes_partition_cost() -> None:
    assert dc_dp_optimization.min_squared_partition_cost([2, 1, 3, 4, 2], 2) == 72


def test_segment_tree_answers_range_sum_queries() -> None:
    tree = segment_tree.SegmentTree([1, 3, 5, 7, 9, 11])
    assert tree.range_sum(1, 3) == 15


def test_fft_convolution_multiplies_signals() -> None:
    result = fft.convolution([1, 2, 3], [4, 5])
    assert [round(value, 6) for value in result] == [4, 13, 22, 15]


def test_randomized_selection_matches_sorted_baseline() -> None:
    rng = random.Random(123)
    for _ in range(50):
        values = [rng.randint(-100, 100) for _ in range(31)]
        k = rng.randrange(len(values))
        expected = sorted(values)[k]
        assert quickselect.kth_smallest(values, k) == expected
        assert median_of_medians.kth_smallest(values, k) == expected


def test_strassen_matches_classical_baseline_for_four_by_four_matrices() -> None:
    a = [
        [1, 2, 3, 4],
        [5, 6, 7, 8],
        [9, 1, 2, 3],
        [4, 5, 6, 7],
    ]
    b = [
        [7, 6, 5, 4],
        [3, 2, 1, 0],
        [1, 3, 5, 7],
        [2, 4, 6, 8],
    ]
    assert strassen_matrix.multiply(a, b) == classical_multiply(a, b)


def test_strassen_pads_non_power_of_two_square_matrices() -> None:
    a = [
        [1, 2, 3],
        [4, 5, 6],
        [7, 8, 9],
    ]
    b = [
        [9, 8, 7],
        [6, 5, 4],
        [3, 2, 1],
    ]
    assert strassen_matrix.multiply(a, b) == classical_multiply(a, b)


def test_degenerate_geometry_cases_are_handled() -> None:
    collinear_hull = convex_hull.monotonic_chain([
        convex_hull.Point(0, 0),
        convex_hull.Point(1, 1),
        convex_hull.Point(2, 2),
        convex_hull.Point(2, 2),
    ])
    assert len(collinear_hull) == 2

    duplicate_distance = closest_pair.find([
        closest_pair.Point(1, 1),
        closest_pair.Point(1, 1),
        closest_pair.Point(5, 5),
    ])
    assert duplicate_distance.distance == 0.0


def test_invalid_inputs_fail_fast() -> None:
    with pytest.raises(ValueError):
        quickselect.kth_smallest([], 0)
    with pytest.raises(ValueError):
        quickselect.kth_smallest([1, 2], 3)
    with pytest.raises(ValueError):
        median_of_medians.kth_smallest([], 0)
    with pytest.raises(ValueError):
        median_of_medians.kth_smallest([1, 2], 4)
    with pytest.raises(ValueError):
        fft.fft([1 + 0j, 2 + 0j, 3 + 0j])
    with pytest.raises(ValueError):
        strassen_matrix.multiply([[1, 2, 3], [4, 5, 6]], [[1, 2], [3, 4]])
    with pytest.raises(ValueError):
        strassen_matrix.multiply(None, [[1]])
    with pytest.raises(ValueError):
        convex_hull.monotonic_chain(None)
    with pytest.raises(ValueError):
        closest_pair.find([closest_pair.Point(0, 0), None])
    with pytest.raises(ValueError):
        fft.fft([1 + 0j, None])
    with pytest.raises(ValueError):
        binary_search.index_of(None, 1)
    with pytest.raises(ValueError):
        merge_sort.sort(None)
    with pytest.raises(ValueError):
        counting_inversions.count(None)
    with pytest.raises(ValueError):
        maximum_subarray.find([])
    with pytest.raises(ValueError):
        exponentiation_by_squaring.pow_int(2, -1)
    with pytest.raises(ValueError):
        polynomial_multiplication.multiply([], [1])
    with pytest.raises(ValueError):
        skyline.skyline(None)
    with pytest.raises(ValueError):
        majority_element.find([])
    with pytest.raises(ValueError):
        rotated_array_search.index_of(None, 1)
    with pytest.raises(ValueError):
        peak_finding.peak_index([])
    with pytest.raises(ValueError):
        two_sorted_selection.kth_smallest([], [], 0)
    with pytest.raises(ValueError):
        dc_dp_optimization.min_squared_partition_cost([], 1)
    with pytest.raises(ValueError):
        segment_tree.SegmentTree([])


def test_python_demo_supports_trace_mode(capsys: pytest.CaptureFixture[str]) -> None:
    demo.main(["--algorithm", "merge-sort", "--trace"])
    captured = capsys.readouterr()
    assert "Merge Sort Trace" in captured.out
    assert captured.err == ""


def test_python_demo_runs_all_algorithm_flows(capsys: pytest.CaptureFixture[str]) -> None:
    demo.main(["--algorithm", "all"])
    captured = capsys.readouterr()
    assert "Strassen Matrix Multiplication" in captured.out
    assert "Exponentiation by Squaring" in captured.out


def test_python_demo_runs_benchmark_mode(capsys: pytest.CaptureFixture[str]) -> None:
    demo.main(["--benchmark", "--sizes", "4"])
    captured = capsys.readouterr()
    assert "Benchmark Mode" in captured.out
    assert "quickselect" in captured.out


def test_python_demo_rejects_invalid_sizes() -> None:
    with pytest.raises(SystemExit) as error:
        demo.main(["--benchmark", "--sizes", "abc"])
    assert error.value.code == 2


def classical_multiply(a: list[list[int]], b: list[list[int]]) -> list[list[int]]:
    return [
        [sum(a[row][inner] * b[inner][col] for inner in range(len(b))) for col in range(len(b[0]))]
        for row in range(len(a))
    ]
