import random

from algorithms.searching import (
    BinarySearchTree,
    RedBlackTree,
    binary_search,
    exponential_search,
    flattened_binary_matrix_search,
    hash_search,
    interpolation_search,
    jump_search,
    staircase_matrix_search,
    ternary_search,
)
from algorithms.sorting import (
    bubble_sort,
    bucket_sort,
    counting_sort,
    heap_sort,
    insertion_sort,
    merge_sort,
    quick_sort,
    radix_sort,
    selection_sort,
    shell_sort,
)


def test_sorting_algorithms_return_sorted_values() -> None:
    values = [5, 1, 4, 2, 8, 0, 2]
    expected = sorted(values)

    for sorter in [
        quick_sort,
        merge_sort,
        heap_sort,
        counting_sort,
        bubble_sort,
        insertion_sort,
        radix_sort,
        selection_sort,
        shell_sort,
    ]:
        assert sorter(values) == expected
    assert bucket_sort([float(value) for value in values]) == [float(value) for value in expected]


def test_searching_algorithms_find_expected_values() -> None:
    values = [1, 2, 4, 5, 8]

    assert binary_search(values, 4) == 2
    assert interpolation_search(values, 4) == 2
    assert exponential_search(values, 4) == 2
    assert jump_search(values, 4) == 2
    assert ternary_search(values, 4) == 2
    assert hash_search(values, 8) == 4
    assert binary_search(values, 99) == -1


def test_matrix_search_variants() -> None:
    row_col_sorted = [
        [1, 4, 7],
        [2, 5, 8],
        [3, 6, 9],
    ]
    flattened_sorted = [
        [1, 3, 5],
        [7, 9, 11],
    ]

    assert staircase_matrix_search(row_col_sorted, 6) == (2, 1)
    assert flattened_binary_matrix_search(flattened_sorted, 9) == (1, 1)


def test_sorting_edge_cases() -> None:
    assert counting_sort([-2, 5, 0, -2]) == [-2, -2, 0, 5]
    assert radix_sort([]) == []
    assert insertion_sort([1]) == [1]
    assert bubble_sort([1, 2, 3]) == [1, 2, 3]


def test_sorting_algorithms_match_python_sorted_for_random_inputs() -> None:
    random.seed(7)
    sorters = [
        quick_sort,
        merge_sort,
        heap_sort,
        counting_sort,
        bubble_sort,
        insertion_sort,
        selection_sort,
        shell_sort,
    ]
    for _ in range(25):
        values = [random.randint(-20, 20) for _ in range(20)]
        expected = sorted(values)
        for sorter in sorters:
            assert sorter(values) == expected

        non_negative = [abs(value) for value in values]
        assert radix_sort(non_negative) == sorted(non_negative)
        floats = [value / 100 for value in non_negative]
        assert bucket_sort(floats) == sorted(floats)


def test_tree_search_algorithms() -> None:
    values = [42, 7, 19, 3, 99, 1]
    for tree_type in [BinarySearchTree, RedBlackTree]:
        tree = tree_type()
        for value in values:
            tree.insert(value)
        assert tree.contains(19) is True
        assert tree.contains(100) is False
        assert tree.inorder() == sorted(values)
