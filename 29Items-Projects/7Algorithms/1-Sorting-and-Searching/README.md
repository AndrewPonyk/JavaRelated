# Sorting and Searching Algorithms

Simple runnable algorithm examples in Python and Java.

Each algorithm has its own file with built-in sample data and console output. There is no API, Docker setup, database, migration, CI pipeline, benchmark service, or framework layer.

## Python

Run one algorithm:

```powershell
python src/python/algorithms/sorting/quick_sort.py
python src/python/algorithms/searching/binary_search.py
```

Run all Python examples:

```powershell
.\scripts\run-python.ps1
```

## Java

Compile all Java files:

```powershell
$buildDir = "out/java"
$sources = Get-ChildItem -Path "src/java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
javac -d $buildDir $sources
```

Run one algorithm:

```powershell
java -cp out/java com.sortingsearching.sorting.QuickSort
java -cp out/java com.sortingsearching.searching.BinarySearch
```

Run all Java examples:

```powershell
.\scripts\run-java.ps1
```

## Algorithms Included

Sorting:

- QuickSort
- MergeSort
- HeapSort
- RadixSort
- CountingSort
- BubbleSort
- InsertionSort
- SelectionSort
- ShellSort
- BucketSort

Searching:

- Binary Search
- Interpolation Search
- Exponential Search
- Jump Search
- Ternary Search
- Hash-based Search
- 2D Matrix Search variants
- Binary Search Tree
- Red-Black Tree

## Tests

Install test tools:

```powershell
python -m pip install -r requirements-dev.txt
```

Run all tests:

```powershell
.\scripts\test.ps1
```
