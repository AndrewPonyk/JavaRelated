from dp_algorithms import (
    climbing_stairs,
    coin_change,
    edit_distance,
    equal_partition,
    fibonacci,
    house_robber,
    knapsack,
    lcs,
    lis,
    longest_common_substring,
    longest_palindromic_subsequence,
    minimum_path_sum,
    subset_sum,
    tsp,
    unique_paths,
    word_break,
)


def test_knapsack_variants_match() -> None:
    weights = [2, 3, 4, 5]
    values = [3, 4, 5, 8]
    assert knapsack.memoized(weights, values, 8).value == 12
    assert knapsack.tabulated(weights, values, 8).details["items"] == [1, 3]
    assert knapsack.space_optimized(weights, values, 8).value == 12


def test_lcs_reconstructs_sequence() -> None:
    result = lcs.tabulated("AGGTAB", "GXTXAYB")
    assert result.value == 4
    assert result.details["sequence"] == "GTAB"


def test_lis_sequence() -> None:
    result = lis.tabulated([10, 9, 2, 5, 3, 7, 101, 18])
    assert result.value == 4
    assert result.details["sequence"] in ([2, 5, 7, 101], [2, 3, 7, 101])


def test_edit_distance() -> None:
    assert edit_distance.space_optimized("kitten", "sitting").value == 3


def test_coin_change() -> None:
    assert coin_change.tabulated([1, 3, 4], 6).value == 2


def test_tsp() -> None:
    dist = [[0, 10, 15, 20], [10, 0, 35, 25], [15, 35, 0, 30], [20, 25, 30, 0]]
    assert tsp.tabulated(dist).value == 80


def test_added_linear_dp_algorithms() -> None:
    assert fibonacci.space_optimized(10).value == 55
    assert climbing_stairs.tabulated(5).value == 8
    assert house_robber.tabulated([2, 7, 9, 3, 1]).value == 12


def test_added_grid_dp_algorithms() -> None:
    assert unique_paths.space_optimized(3, 7).value == 28
    grid = [[1, 3, 1], [1, 5, 1], [4, 2, 1]]
    assert minimum_path_sum.tabulated(grid).value == 7


def test_added_subset_dp_algorithms() -> None:
    assert subset_sum.tabulated([3, 34, 4, 12, 5, 2], 9).value is True
    assert equal_partition.space_optimized([1, 5, 11, 5]).value is True


def test_added_string_dp_algorithms() -> None:
    assert longest_common_substring.tabulated("ABABC", "BABCA").details["substring"] == "BABC"
    assert longest_palindromic_subsequence.space_optimized("bbbab").value == 4
    assert word_break.tabulated("leetcode", {"leet", "code"}).details["words"] == ["leet", "code"]
