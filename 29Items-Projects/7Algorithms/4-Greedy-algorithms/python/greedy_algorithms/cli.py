from greedy_algorithms.algorithms.activity_selection import Activity, select_activities
from greedy_algorithms.algorithms.fractional_knapsack import Item, fractional_knapsack
from greedy_algorithms.algorithms.huffman import huffman_codes
from greedy_algorithms.algorithms.interval_scheduling import Interval, schedule_intervals
from greedy_algorithms.algorithms.job_scheduling import Job, schedule_jobs
from greedy_algorithms.algorithms.mst import Edge, kruskal_mst
from greedy_algorithms.console_view import ConsoleView


def main() -> None:
    view = ConsoleView()

    activities = [
        Activity("A1", 1, 4),
        Activity("A2", 3, 5),
        Activity("A3", 0, 6),
        Activity("A4", 5, 7),
        Activity("A5", 8, 9),
        Activity("A6", 5, 9),
    ]
    view.run_demo(
        "Activity Selection",
        lambda: (
            select_activities(activities),
            "choosing the earliest finishing compatible activity leaves maximum room for the rest.",
        ),
    )

    intervals = [
        Interval("I1", 0, 3),
        Interval("I2", 1, 4),
        Interval("I3", 3, 5),
        Interval("I4", 4, 7),
        Interval("I5", 5, 9),
    ]
    view.run_demo(
        "Interval Scheduling",
        lambda: (
            schedule_intervals(intervals),
            "the exchange argument replaces any first choice with the earliest finisher.",
        ),
    )

    items = [Item("Gold", 60, 10), Item("Silver", 100, 20), Item("Bronze", 120, 30)]
    view.run_demo(
        "Fractional Knapsack",
        lambda: (
            fractional_knapsack(items, capacity=50),
            "taking highest value density first is optimal because fractions can be exchanged locally.",
        ),
    )

    jobs = [
        Job("J1", 2, 100),
        Job("J2", 1, 19),
        Job("J3", 2, 27),
        Job("J4", 1, 25),
        Job("J5", 3, 15),
    ]
    view.run_demo(
        "Job Scheduling",
        lambda: (
            schedule_jobs(jobs),
            "scheduling higher-profit jobs as late as possible preserves earlier slots for tighter jobs.",
        ),
    )

    view.run_demo(
        "Huffman Coding",
        lambda: (
            huffman_codes("greedy algorithms"),
            "merging the two least frequent symbols creates an optimal prefix-code subtree.",
        ),
    )

    edges = [
        Edge(0, 1, 10),
        Edge(0, 2, 6),
        Edge(0, 3, 5),
        Edge(1, 3, 15),
        Edge(2, 3, 4),
    ]
    view.run_demo(
        "Minimum Spanning Tree",
        lambda: (
            kruskal_mst(vertex_count=4, edges=edges),
            "the cut property allows choosing the lightest edge crossing any cut.",
        ),
    )


if __name__ == "__main__":
    main()
