# DIVIDE-CONQUER Project Plan

## 1. Project File Structure

This project is an educational algorithms workspace. The primary product surface is local console execution in Java and Python.

```text
5-DIVIDE-CONQUER/
|-- .github/
|   `-- workflows/
|       `-- ci.yml
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- scripts/
|   |-- run_java_demo.ps1
|   |-- run_python_demo.ps1
|   `-- test_all.ps1
|-- src/
|   |-- main/
|   |   |-- java/com/divideconquer/
|   |   |   |-- algorithms/
|   |   |   |   |-- BinarySearch.java
|   |   |   |   |-- ClosestPair.java
|   |   |   |   |-- ConvexHull.java
|   |   |   |   |-- CountingInversions.java
|   |   |   |   |-- DivideConquerDpOptimization.java
|   |   |   |   |-- ExponentiationBySquaring.java
|   |   |   |   |-- FastFourierTransform.java
|   |   |   |   |-- Karatsuba.java
|   |   |   |   |-- MajorityElement.java
|   |   |   |   |-- MaximumSubarray.java
|   |   |   |   |-- MedianOfMedians.java
|   |   |   |   |-- MergeSort.java
|   |   |   |   |-- PeakFinding.java
|   |   |   |   |-- PolynomialMultiplication.java
|   |   |   |   |-- Quickselect.java
|   |   |   |   |-- RotatedArraySearch.java
|   |   |   |   |-- SegmentTree.java
|   |   |   |   |-- SelectionInTwoSortedArrays.java
|   |   |   |   |-- SkylineProblem.java
|   |   |   |   `-- StrassenMatrix.java
|   |   |   |   `-- ToomCookMultiplication.java
|   |   |   `-- app/
|   |   |       `-- DemoRunner.java
|   |   `-- python/divide_conquer/
|   |       |-- __init__.py
|   |       |-- demo.py
|   |       `-- algorithms/
|   |           |-- __init__.py
|   |           |-- binary_search.py
|   |           |-- closest_pair.py
|   |           |-- convex_hull.py
|   |           |-- counting_inversions.py
|   |           |-- dc_dp_optimization.py
|   |           |-- exponentiation_by_squaring.py
|   |           |-- fft.py
|   |           |-- karatsuba.py
|   |           |-- majority_element.py
|   |           |-- maximum_subarray.py
|   |           |-- median_of_medians.py
|   |           |-- merge_sort.py
|   |           |-- peak_finding.py
|   |           |-- polynomial_multiplication.py
|   |           |-- quickselect.py
|   |           |-- rotated_array_search.py
|   |           |-- segment_tree.py
|   |           |-- skyline.py
|   |           `-- strassen_matrix.py
|   |           `-- toom_cook.py
|   |           `-- two_sorted_selection.py
|   `-- test/
|       |-- java/com/divideconquer/AlgorithmSmokeTest.java
|       `-- python/test_algorithms.py
|-- .env.example
|-- .gitignore
|-- pom.xml
|-- pyproject.toml
|-- requirements-dev.txt
`-- README.md
```

### Source Code

The code is split by language while keeping algorithm names aligned:

- Java package: `com.divideconquer.algorithms`
- Python package: `divide_conquer.algorithms`
- Demo entry points:
  - Java: `com.divideconquer.app.DemoRunner`
  - Python: `python -m divide_conquer.demo`

Each algorithm exposes a deterministic API and returns plain values such as matrices, numbers, points, hull vertices, or complex values. The demo layer handles CLI flags, console formatting, Master theorem notes, trace output, and benchmark tables.

### CLI Features

- `--algorithm <name>` runs one algorithm or `all`.
- `--benchmark` prints deterministic timing tables.
- `--sizes <list>` controls benchmark sizes, for example `16,64,256,1024`.
- `--trace` prints ASCII recursive split/combine traces.
- `--help` documents available flags.

### CI/CD

CI is a quality gate:

- Java compile and tests through Maven.
- Python tests through pytest.
- Python lint checks through Ruff.
- No deploy stage because the project is local educational software.
- No deploy stage because the project is local educational software.

### Tools Configuration

- `pom.xml` for Maven build/test execution.
- `pyproject.toml` for Python packaging, pytest, and Ruff settings.
- `requirements-dev.txt` for Python test/lint tooling.
- `.env.example` for harmless local knobs.
- `.github/workflows/ci.yml` for repository CI.
The project contains only local Java/Python algorithm source, tests, docs, and tool configuration.

## 2. Implementation TODO List

### Phase 1: Foundation (High Priority)

- [x] Create language-specific source trees.
- [x] Add Java Maven configuration.
- [x] Add Python package configuration.
- [x] Add console demo entry points.
- [x] Add CI quality-gate workflow.
- [x] Document the local Java/Python educational scope.

### Phase 2: Core Features (Medium Priority)

- [x] Implement Strassen matrix multiplication in Java and Python.
- [x] Implement closest pair of points in Java and Python.
- [x] Implement convex hull in Java and Python.
- [x] Implement FFT in Java and Python.
- [x] Implement Karatsuba multiplication in Java and Python.
- [x] Implement Quickselect in Java and Python.
- [x] Implement Median of Medians in Java and Python.
- [x] Implement Binary Search in Java and Python.
- [x] Implement Merge Sort in Java and Python.
- [x] Implement Counting Inversions in Java and Python.
- [x] Implement Maximum Subarray in Java and Python.
- [x] Implement Exponentiation by Squaring in Java and Python.
- [x] Implement Toom-Cook Multiplication in Java and Python.
- [x] Implement Polynomial Multiplication in Java and Python.
- [x] Implement Skyline Problem in Java and Python.
- [x] Implement Majority Element in Java and Python.
- [x] Implement Search in Rotated Sorted Array in Java and Python.
- [x] Implement Peak Finding in Java and Python.
- [x] Implement Selection in Two Sorted Arrays in Java and Python.
- [x] Implement Divide-and-Conquer DP Optimization in Java and Python.
- [x] Implement Segment Tree Construction in Java and Python.
- [x] Implement FFT convolution/inverse FFT variant in Java and Python.
- [x] Print clear demo output with result and complexity notes.
- [x] Add smoke tests for the core algorithms.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Add benchmark mode for input sizes such as 16, 64, 256, and 1024.
- [x] Add randomized property tests comparing optimized algorithms with simple baselines.
- [x] Add visual ASCII traces for recursive splitting and combining.
- [x] Add CLI flags to run one algorithm at a time.
- [x] Add larger examples with timing tables.
- [x] Add extended Master theorem notes per recurrence.
