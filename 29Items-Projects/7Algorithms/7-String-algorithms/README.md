# STRING ALGORITHMS

Console demos and tests for classic string algorithms in Java and Python.

| Category | Algorithm | Java implementation | Python implementation | Purpose |
| --- | --- | --- | --- | --- |
| Single-pattern matching | KMP | `KmpMatcher.java` | `kmp.py` | Find all exact pattern matches using prefix preprocessing |
| Single-pattern matching | Rabin-Karp | `RabinKarp.java` | `rabin_karp.py` | Find exact matches with rolling hash and collision verification |
| Multiple-pattern matching | Aho-Corasick | `AhoCorasick.java` | `aho_corasick.py` | Find many patterns in one text scan |
| Prefix data structure | Trie | `Trie.java` | `trie.py` | Insert, exact-search, and prefix-search words |
| Suffix indexing | Suffix Array | `SuffixArray.java` | `suffix_array.py` | Build sorted suffix indexes and search substrings |
| Suffix indexing | Suffix Tree | `SuffixTree.java` | `suffix_tree.py` | Compact suffix structure for substring lookup |
| Palindrome processing | Manacher | `Manacher.java` | `manacher.py` | Find the longest palindromic substring |
| Sequence comparison | Longest Common Subsequence | `LongestCommonSubsequence.java` | `lcs.py` | Find the longest sequence shared in order, not necessarily contiguous |
| Sequence comparison | Longest Common Substring | `LongestCommonSubstring.java` | `longest_common_substring.py` | Find the longest contiguous shared substring |

## Requirements

- Java 11+
- Maven 3.8+
- Python 3.10+

## Setup

Install Python test tooling:

```powershell
python -m pip install -r requirements-dev.txt
```

## Run Java

```powershell
mvn verify
mvn exec:java
```

or:

```powershell
.\tools\run-java.ps1
```

Custom input:

```powershell
mvn exec:java "-Dexec.args=--text mississippi --pattern issi --patterns is,ssi,miss --palindrome-text abacdfgdcaba --comparison-text missouri"
```

## Run Python

```powershell
$env:PYTHONPATH = "python"
python -m string_algorithms
python -m pytest
New-Item -ItemType Directory -Force -Path coverage-data | Out-Null
python -m coverage run -m pytest
python -m coverage report
```

or:

```powershell
.\tools\run-python.ps1
```

Custom input:

```powershell
$env:PYTHONPATH = "python"
python -m string_algorithms --text mississippi --pattern issi --patterns is,ssi,miss --palindrome-text abacdfgdcaba --comparison-text missouri
```

## Run Everything

```powershell
.\scripts\test.ps1
```

## Troubleshooting

- If `mvn verify` cannot download plugins, retry after network access is stable; Maven caches dependencies under the user `.m2` directory.
- If Python cannot import `string_algorithms`, run commands from the repository root and set `$env:PYTHONPATH = "python"`.
- If coverage reports fail because of a locked local file, rerun `.\scripts\test.ps1`; Python coverage data is written under `coverage-data/`.
- This project has no API server, database, frontend, or Docker setup by design.

## Environment Variables

```dotenv
STRING_ALGORITHMS_SAMPLE_TEXT=abracadabra pattern matching abracadabra
STRING_ALGORITHMS_PATTERN=abra
STRING_ALGORITHMS_PATTERNS=abra,cad,match
STRING_ALGORITHMS_PALINDROME_TEXT=forgeeksskeegfor
STRING_ALGORITHMS_COMPARISON_TEXT=cadabra matching
STRING_ALGORITHMS_BENCHMARK=false
```

## Complexity

| Algorithm | Main operation | Typical complexity |
| --- | --- | --- |
| KMP | Single-pattern search | O(n + m) |
| Rabin-Karp | Rolling-hash search with collision check | Average O(n + m) |
| Aho-Corasick | Multiple-pattern search | O(text length + matches) after preprocessing |
| Suffix Array | Build and search | Educational O(n log n) build, O(n) demo search |
| Suffix Tree | Build and contains | O(n^2) suffix insertion build, O(m) contains |
| Manacher | Longest palindromic substring | O(n) |
| Trie | Insert/search/prefix | O(k) |
| Longest Common Subsequence | Dynamic programming sequence comparison | O(n * m) |
| Longest Common Substring | Dynamic programming contiguous comparison | O(n * m) |

## Scope

This is a local learning project. It intentionally has no frontend, backend API, database, Docker setup, or deployment target.
