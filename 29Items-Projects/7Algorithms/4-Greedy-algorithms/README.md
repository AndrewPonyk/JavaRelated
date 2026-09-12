# GREEDY Algorithms

Learning project for greedy choice property demonstrations in Java and Python.

Algorithms included:

- Activity Selection
- Huffman Coding
- Fractional Knapsack
- Job Scheduling with Deadlines
- Minimum Spanning Tree with Kruskal's algorithm
- Interval Scheduling

## Run Python

```powershell
.\scripts\run-python.ps1
```

Or:

```powershell
cd python
python -m greedy_algorithms
```

## Run Java

```powershell
.\scripts\run-java.ps1
```

Or:

```powershell
cd java
mvn test
mvn exec:java
```

## Algorithm Files

Java implementations:

- `java/src/main/java/com/example/greedy/algorithms/ActivitySelection.java`
- `java/src/main/java/com/example/greedy/algorithms/FractionalKnapsack.java`
- `java/src/main/java/com/example/greedy/algorithms/HuffmanCoding.java`
- `java/src/main/java/com/example/greedy/algorithms/IntervalScheduling.java`
- `java/src/main/java/com/example/greedy/algorithms/JobScheduling.java`
- `java/src/main/java/com/example/greedy/algorithms/KruskalMst.java`

Python implementations:

- `python/greedy_algorithms/algorithms/activity_selection.py`
- `python/greedy_algorithms/algorithms/fractional_knapsack.py`
- `python/greedy_algorithms/algorithms/huffman.py`
- `python/greedy_algorithms/algorithms/interval_scheduling.py`
- `python/greedy_algorithms/algorithms/job_scheduling.py`
- `python/greedy_algorithms/algorithms/mst.py`

## Project Notes

- Documentation lives in `docs/`.
- Java source lives in `java/src/main/java`.
- Python source lives in `python/greedy_algorithms`.
- This is intended as an algorithms learning project. Database/API files are optional stubs and are not needed to run the demos.
