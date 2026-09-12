# DIVIDE-CONQUER

Educational Java and Python implementations of classic divide-and-conquer algorithms:

- Strassen Matrix Multiplication
- Closest Pair of Points
- Convex Hull
- Fast Fourier Transform
- Karatsuba Multiplication
- Quickselect
- Median of Medians
- Binary Search
- Merge Sort
- Counting Inversions
- Maximum Subarray
- Exponentiation by Squaring
- Toom-Cook Multiplication
- Polynomial Multiplication
- Skyline Problem
- Majority Element
- Search in Rotated Sorted Array
- Peak Finding
- Selection in Two Sorted Arrays
- Divide-and-Conquer DP Optimization
- Segment Tree Construction
- FFT Convolution / Inverse FFT Variant

The project is intentionally local-only because the goal is algorithm learning through clear console output.

## Requirements

- Java 11+
- Maven 3.9+
- Python 3.11+

## Run Java Demo

```powershell
.\scripts\run_java_demo.ps1
```

Run one algorithm with a trace:

```powershell
.\scripts\run_java_demo.ps1 --algorithm merge-sort --trace
```

Run benchmark mode:

```powershell
.\scripts\run_java_demo.ps1 --benchmark --sizes 16,64
```

Equivalent commands:

```powershell
mvn -q compile
java -cp target/classes com.divideconquer.app.DemoRunner
```

## Run Python Demo

```powershell
.\scripts\run_python_demo.ps1
```

Run one algorithm with a trace:

```powershell
.\scripts\run_python_demo.ps1 --algorithm merge-sort --trace
```

Run benchmark mode:

```powershell
.\scripts\run_python_demo.ps1 --benchmark --sizes 16,64
```

Equivalent Python command:

```powershell
$env:PYTHONPATH="src/main/python"
python -m divide_conquer.demo
```

## Run Tests

```powershell
.\scripts\test_all.ps1
```

Or run each language separately:

```powershell
mvn test
$env:PYTHONPATH="src/main/python"
pytest
```

Run Python tests with the same 80% coverage gate used by `test_all.ps1`:

```powershell
$coverageFile = "target/.coverage-python-$PID"
coverage run --data-file=$coverageFile -m pytest
coverage report --data-file=$coverageFile
```

## CLI Options

Both Java and Python demos support:

- `--algorithm <name>`: `strassen`, `closest-pair`, `convex-hull`, `fft`, `karatsuba`, `quickselect`, `median-of-medians`, or `all`
- Additional algorithm names: `binary-search`, `merge-sort`, `counting-inversions`, `maximum-subarray`, `exponentiation`
- Advanced algorithm names: `toom-cook`, `polynomial`, `skyline`, `majority`, `rotated-search`, `peak-finding`, `two-sorted-selection`, `dc-dp`, `segment-tree`, `fft-convolution`
- `--benchmark`: print deterministic timing rows
- `--sizes <list>`: benchmark sizes such as `16,64,256,1024`
- `--trace`: print ASCII recursive split/combine traces
- `--help`: show usage

## Notes

FFT validates that input length is a power of two. Strassen accepts square matrices and pads non-power-of-two sizes internally so the public API is easier to use.

Example single-algorithm runs:

```powershell
.\scripts\run_java_demo.ps1 --algorithm binary-search
.\scripts\run_python_demo.ps1 --algorithm maximum-subarray
.\scripts\run_java_demo.ps1 --algorithm exponentiation
.\scripts\run_python_demo.ps1 --algorithm skyline
.\scripts\run_java_demo.ps1 --algorithm fft-convolution
```

## Troubleshooting

- If `mvn test` fails with `release version 11 not supported`, install JDK 11+ and make sure `JAVA_HOME` points to it.
- If `pytest` cannot import `divide_conquer`, set `PYTHONPATH` to `src/main/python` or use `.\scripts\test_all.ps1`.
- If a stale local coverage file is locked on Windows, rerun `.\scripts\test_all.ps1`; it writes coverage data to a process-specific file under `target/`.

## Algorithm Reference

### Strassen Matrix Multiplication
Multiplies square matrices by splitting each matrix into quadrants and using 7 recursive multiplications instead of 8.

Example: `[[1,2],[3,4]] * [[5,6],[7,8]] = [[19,22],[43,50]]`

Complexity: `O(n^log2(7))`, approximately `O(n^2.807)`.

### Closest Pair of Points
Finds the two closest points in a 2D plane by splitting points by x-coordinate and checking a narrow middle strip.

Example: points `(3,1)` and `(3,2)` have distance `1.0`.

Complexity: `O(n log n)`.

### Convex Hull
Finds the outer boundary around a set of points. This project uses the monotonic chain method.

Example: square corners remain in the hull while an interior point is removed.

Complexity: `O(n log n)` because points are sorted first.

### Fast Fourier Transform
Computes the discrete Fourier transform by recursively splitting even and odd indexed values.

Example: FFT of `[1,2,3,4]` produces frequency values starting with `10`.

Complexity: `O(n log n)`.

### Karatsuba Multiplication
Multiplies large integers using 3 recursive multiplications instead of 4.

Example: `1234 * 5678 = 7006652`.

Complexity: `O(n^log2(3))`, approximately `O(n^1.585)`.

### Quickselect
Finds the kth smallest element by partitioning around a pivot and recursing into one side.

Example: 3rd smallest in `[9,1,8,2,7,3,6]` is `3`.

Complexity: average `O(n)`, worst-case `O(n^2)`.

### Median of Medians
Deterministic selection algorithm that chooses a strong pivot using groups of five.

Example: median of `[9,1,8,2,7,3,6,4,5]` is `5`.

Complexity: worst-case `O(n)`.

### Binary Search
Searches a sorted array by repeatedly discarding half of the remaining range.

Example: find `9` in `[1,3,5,7,9,11,13]`, result index is `4`.

Complexity: `O(log n)`.

### Merge Sort
Sorts by recursively splitting the array, sorting both halves, and merging them.

Example: `[8,3,7,4]` becomes `[3,4,7,8]`.

Complexity: `O(n log n)`.

### Counting Inversions
Counts pairs `(i, j)` where `i < j` and `a[i] > a[j]`, using merge-sort style merging.

Example: `[2,4,1,3,5]` has `3` inversions.

Complexity: `O(n log n)`.

### Maximum Subarray
Finds the contiguous subarray with the maximum sum by comparing left, right, and crossing solutions.

Example: in `[-2,1,-3,4,-1,2,1,-5,4]`, best sum is `6` from `[4,-1,2,1]`.

Complexity: `O(n log n)` for the divide-and-conquer version.

### Exponentiation by Squaring
Computes powers by recursively squaring half-powers.

Example: `3^13 = 1594323`.

Complexity: `O(log n)` multiplications.

### Toom-Cook Multiplication
Splits large integers into three parts, evaluates them at several points, recursively multiplies, then interpolates.

Example: `123456789012345 * 987654321098765 = 121932631137021071359549253925`.

Complexity: Toom-3 is about `O(n^log3(5))`, approximately `O(n^1.465)`.

### Polynomial Multiplication
Multiplies coefficient arrays using recursive splitting.

Example: `[1,2,3] * [4,5] = [4,13,22,15]`.

Complexity: this Karatsuba-style version is approximately `O(n^1.585)` for balanced inputs.

### Skyline Problem
Computes the outline formed by overlapping buildings using divide-and-conquer merging.

Example: overlapping buildings produce key points like `(2,10), (3,15), (7,12), (12,0)`.

Complexity: `O(n log n)`.

### Majority Element
Finds the value that appears more than `n/2` times, if one exists.

Example: `[2,2,1,2,3,2,2]` has majority element `2`.

Complexity: `O(n log n)` for this divide-and-conquer candidate-counting version.

### Search in Rotated Sorted Array
Searches a sorted array that has been rotated by identifying which half is still sorted.

Example: find `8` in `[13,18,25,2,8,10]`, result index is `4`.

Complexity: `O(log n)`.

### Peak Finding
Finds an index whose value is not smaller than its neighbors by following the rising side.

Example: `[1,3,7,12,9,5]` has peak value `12`.

Complexity: `O(log n)`.

### Selection in Two Sorted Arrays
Finds the kth smallest value across two sorted arrays by discarding about half of the search space each step.

Example: kth value from `[1,4,7,10]` and `[2,3,6,8,9]` can be found without merging.

Complexity: `O(log k)`.

### Divide-and-Conquer DP Optimization
Optimizes dynamic programming rows when the best split point moves monotonically.

Example: partition `[2,1,3,4,2]` into `2` groups minimizing squared group sums.

Complexity: often improves a DP row from `O(n^2)` to `O(n log n)`; total depends on number of DP rows.

### Segment Tree Construction
Builds a tree over array intervals so range queries can be answered quickly.

Example: range sum `[1,3]` over `[1,3,5,7,9,11]` is `15`.

Complexity: build `O(n)`, query `O(log n)`.

### FFT Convolution / Inverse FFT
Uses FFT, pointwise multiplication, and inverse FFT to convolve two signals or multiply coefficient arrays.

Example: convolution of `[1,2,3]` and `[4,5]` is `[4,13,22,15]`.

Complexity: `O(n log n)`.
