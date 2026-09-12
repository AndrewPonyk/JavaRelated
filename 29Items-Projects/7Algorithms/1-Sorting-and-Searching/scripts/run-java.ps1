$ErrorActionPreference = "Stop"

$buildDir = "out/java"
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
$sources = Get-ChildItem -Path "src/java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
javac -d $buildDir $sources

java -cp $buildDir com.sortingsearching.sorting.QuickSort
java -cp $buildDir com.sortingsearching.sorting.MergeSort
java -cp $buildDir com.sortingsearching.sorting.HeapSort
java -cp $buildDir com.sortingsearching.sorting.RadixSort
java -cp $buildDir com.sortingsearching.sorting.CountingSort
java -cp $buildDir com.sortingsearching.sorting.BubbleSort
java -cp $buildDir com.sortingsearching.sorting.InsertionSort
java -cp $buildDir com.sortingsearching.sorting.SelectionSort
java -cp $buildDir com.sortingsearching.sorting.ShellSort
java -cp $buildDir com.sortingsearching.sorting.BucketSort

java -cp $buildDir com.sortingsearching.searching.BinarySearch
java -cp $buildDir com.sortingsearching.searching.InterpolationSearch
java -cp $buildDir com.sortingsearching.searching.ExponentialSearch
java -cp $buildDir com.sortingsearching.searching.JumpSearch
java -cp $buildDir com.sortingsearching.searching.TernarySearch
java -cp $buildDir com.sortingsearching.searching.HashSearch
java -cp $buildDir com.sortingsearching.searching.MatrixSearch
java -cp $buildDir com.sortingsearching.searching.BinarySearchTree
java -cp $buildDir com.sortingsearching.searching.RedBlackTree
