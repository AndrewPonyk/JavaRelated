from greedy_algorithms.algorithms.activity_selection import Activity, select_activities
from greedy_algorithms.algorithms.fractional_knapsack import Item, fractional_knapsack
from greedy_algorithms.algorithms.huffman import huffman_codes
from greedy_algorithms.algorithms.mst import Edge, kruskal_mst


def test_activity_selection_prefers_earliest_finish() -> None:
    selected = select_activities(
        [
            Activity("A1", 1, 4),
            Activity("A2", 3, 5),
            Activity("A3", 5, 7),
            Activity("A4", 6, 9),
        ]
    )

    assert [activity.name for activity in selected] == ["A1", "A3"]


def test_fractional_knapsack_allows_partial_item() -> None:
    result = fractional_knapsack(
        [Item("Gold", 60, 10), Item("Silver", 100, 20), Item("Bronze", 120, 30)],
        capacity=50,
    )

    assert result["total_value"] == 240.0


def test_huffman_single_character_text() -> None:
    assert huffman_codes("aaaa") == {"a": "0"}


def test_kruskal_mst_total_weight() -> None:
    result = kruskal_mst(
        4,
        [
            Edge(0, 1, 10),
            Edge(0, 2, 6),
            Edge(0, 3, 5),
            Edge(1, 3, 15),
            Edge(2, 3, 4),
        ],
    )

    assert result["total_weight"] == 19
