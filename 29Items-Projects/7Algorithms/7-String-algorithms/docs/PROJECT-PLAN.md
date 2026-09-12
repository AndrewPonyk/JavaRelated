# STRING ALGORITHMS Project Plan

## 1.1 Project File Structure

This project is a console-first learning workspace for classic string algorithms in Java and Python. There is no frontend, backend service, database, Docker setup, or production deployment target because the business requirement is local execution with clear console output.

```text
.
|-- .github/workflows/ci.yml
|-- data/benchmark-text.txt
|-- docs/
|   |-- ARCHITECTURE.md
|   |-- PROJECT-PLAN.md
|   `-- TECH-NOTES.md
|-- python/string_algorithms/
|   |-- __init__.py
|   |-- __main__.py
|   |-- algorithms/
|   |   |-- aho_corasick.py
|   |   |-- kmp.py
|   |   |-- lcs.py
|   |   |-- longest_common_substring.py
|   |   |-- manacher.py
|   |   |-- rabin_karp.py
|   |   |-- suffix_array.py
|   |   |-- suffix_tree.py
|   |   `-- trie.py
|   `-- demo/runner.py
|-- scripts/test.ps1
|-- src/main/java/com/stringalgorithms/
|   |-- Main.java
|   |-- algorithms/
|   |   |-- AhoCorasick.java
|   |   |-- KmpMatcher.java
|   |   |-- LongestCommonSubsequence.java
|   |   |-- LongestCommonSubstring.java
|   |   |-- Manacher.java
|   |   |-- RabinKarp.java
|   |   |-- SuffixArray.java
|   |   |-- SuffixTree.java
|   |   `-- Trie.java
|   |-- demo/ConsoleDemo.java
|   `-- model/MatchResult.java
|-- src/test/java/com/stringalgorithms/AlgorithmsSmokeTest.java
|-- tests/test_algorithms.py
|-- tools/
|   |-- run-java.ps1
|   `-- run-python.ps1
|-- .editorconfig
|-- .env.example
|-- .gitignore
|-- GPT-5.txt
|-- pom.xml
|-- pyproject.toml
|-- README.md
`-- requirements-dev.txt
```

### Source Code

- Java source lives under `src/main/java/com/stringalgorithms`.
- Python source lives under `python/string_algorithms`.
- Each algorithm is isolated in its own file for study and testing.
- Console orchestration is separated from algorithm code:
  - Java: `ConsoleDemo`
  - Python: `string_algorithms.demo.runner`

### Frontend, Backend, Shared Modules, Database

- Frontend: not applicable. Console output is the user interface.
- Backend/API: not applicable. There is no server boundary or remote client.
- Shared modules: language-local algorithm packages are used instead of cross-language shared code.
- Database migrations: not applicable. Algorithms operate on in-memory strings and patterns.
- Configuration: `.env.example` documents local demo/test knobs.

### CI/CD

- GitHub Actions workflow: `.github/workflows/ci.yml`.
- Pipeline validates both languages:
  - Java compile and tests through Maven.
  - Java console smoke run.
  - Python tests through pytest.
  - Python console smoke run.
- No deploy stages are configured because the project is local-only.

### Tools Configuration

- `pom.xml`: Java 11 build, test, and packaging.
- `pyproject.toml`: Python packaging and test configuration.
- `requirements-dev.txt`: Python test dependency list.
- `.editorconfig`: consistent editor defaults.
- `.gitignore`: excludes generated build, cache, and IDE files.
- `.env.example`: local execution defaults.

## 1.2 Implementation Checklist

### Phase 1: Foundation (High Priority)

- [x] Create project directory structure.
- [x] Add Java and Python package skeletons.
- [x] Add console demo entry points for both languages.
- [x] Add baseline documentation.
- [x] Add CI workflow.
- [x] Confirm required local tool versions on developer machines.
- [x] Add README examples for each supported execution path.

### Phase 2: Core Features (Medium Priority)

- [x] Implement KMP matching in Java and Python.
- [x] Implement Rabin-Karp matching in Java and Python.
- [x] Implement Aho-Corasick multiple-pattern matching in Java and Python.
- [x] Implement Trie insert/search/prefix behavior in Java and Python.
- [x] Implement Suffix Array construction and search in Java and Python.
- [x] Implement compact Suffix Tree construction and contains lookup in Java and Python.
- [x] Implement Manacher longest-palindrome lookup in Java and Python.
- [x] Implement Longest Common Subsequence in Java and Python.
- [x] Implement Longest Common Substring in Java and Python.
- [x] Add richer edge-case tests for empty strings, repeated characters, and Unicode.
- [x] Add benchmark fixtures with repeatable input data.

### Phase 3: Polish & Optimization (Lower Priority)

- [x] Add optional CLI arguments for custom text and pattern input.
- [x] Add algorithm complexity tables to README.
- [x] Add performance comparison output for larger sample texts.
- [x] Keep Docker out of scope as required for this algorithms-only console project.
- [x] Keep generated docs out of scope until public API documentation is needed.
