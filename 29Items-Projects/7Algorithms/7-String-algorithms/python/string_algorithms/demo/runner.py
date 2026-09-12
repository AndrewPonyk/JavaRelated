from __future__ import annotations

import argparse
import os
import time

from string_algorithms.algorithms import (
    AhoCorasick,
    SuffixArray,
    SuffixTree,
    Trie,
    kmp_search,
    longest_common_subsequence,
    longest_common_substring,
    longest_palindrome,
    rabin_karp_search,
)

DEFAULT_TEXT = "abracadabra pattern matching abracadabra"
DEFAULT_PATTERN = "abra"
DEFAULT_PATTERNS = "abra,cad,match"
DEFAULT_PALINDROME_TEXT = "forgeeksskeegfor"
DEFAULT_COMPARISON_TEXT = "cadabra matching"


def run(argv: list[str] | None = None) -> None:
    config = _parse_args(argv)
    text = config.text
    pattern = config.pattern
    patterns = config.patterns

    _print_header("Python String Algorithm Demo")
    print(f"Text: {text}")
    print(f"Pattern: {pattern}")
    print(f"Patterns: {patterns}")
    print(f"Comparison text: {config.comparison_text}")
    _print_matches("KMP", pattern, kmp_search(text, pattern))
    _print_matches("Rabin-Karp", pattern, rabin_karp_search(text, pattern))

    aho_corasick = AhoCorasick(patterns)
    print("Aho-Corasick matches:")
    for result in aho_corasick.search(text):
        print(f"  pattern={result.pattern} index={result.start_index}")

    suffix_array = SuffixArray(text)
    _print_matches("Suffix Array", pattern, suffix_array.search(pattern))

    suffix_tree = SuffixTree(text)
    print(f"Suffix Tree contains '{pattern}': {suffix_tree.contains(pattern)}")
    print(f"Suffix Tree edge count: {suffix_tree.edge_count}")

    print(
        "Manacher longest palindrome "
        f"in '{config.palindrome_text}': {longest_palindrome(config.palindrome_text)}"
    )

    trie = Trie()
    for value in patterns:
        trie.insert(value)
    print(f"Trie contains '{patterns[0]}': {trie.contains(patterns[0])}")
    print(f"Trie starts_with '{pattern[:3]}': {trie.starts_with(pattern[:3])}")

    lcs = longest_common_subsequence(text, config.comparison_text)
    common_substring = longest_common_substring(text, config.comparison_text)
    print(f"Longest Common Subsequence with comparison text: '{lcs}' (length={len(lcs)})")
    print(
        "Longest Common Substring with comparison text: "
        f"'{common_substring}' (length={len(common_substring)})"
    )

    if config.benchmark:
        _print_benchmark(text, pattern, patterns, config.comparison_text)


def _print_header(title: str) -> None:
    print("=" * len(title))
    print(title)
    print("=" * len(title))


def _print_matches(algorithm: str, pattern: str, indexes: list[int]) -> None:
    print(f"{algorithm} matches for '{pattern}': {indexes}")


def _print_benchmark(
    text: str, pattern: str, patterns: list[str], comparison_text: str
) -> None:
    print("Benchmark snapshot:")
    _measure("KMP", lambda: kmp_search(text, pattern))
    _measure("Rabin-Karp", lambda: rabin_karp_search(text, pattern))
    _measure("Aho-Corasick", lambda: AhoCorasick(patterns).search(text))
    _measure("Suffix Array build+search", lambda: SuffixArray(text).search(pattern))
    _measure("Suffix Tree build+contains", lambda: SuffixTree(text).contains(pattern))
    _measure("Manacher", lambda: longest_palindrome(text))
    _measure("LCS", lambda: longest_common_subsequence(text, comparison_text))
    _measure("Longest Common Substring", lambda: longest_common_substring(text, comparison_text))


def _measure(label: str, operation) -> None:
    start = time.perf_counter_ns()
    operation()
    elapsed_micros = (time.perf_counter_ns() - start) // 1_000
    print(f"  {label:<24} {elapsed_micros} microseconds")


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run string algorithm console demos.")
    parser.add_argument(
        "--text",
        default=os.getenv("STRING_ALGORITHMS_SAMPLE_TEXT", DEFAULT_TEXT),
        help="Text to search.",
    )
    parser.add_argument(
        "--pattern",
        default=os.getenv("STRING_ALGORITHMS_PATTERN", DEFAULT_PATTERN),
        help="Single pattern for KMP, Rabin-Karp, and suffix searches.",
    )
    parser.add_argument(
        "--patterns",
        default=os.getenv("STRING_ALGORITHMS_PATTERNS", DEFAULT_PATTERNS),
        help="Comma-separated patterns for Aho-Corasick and Trie.",
    )
    parser.add_argument(
        "--palindrome-text",
        default=os.getenv("STRING_ALGORITHMS_PALINDROME_TEXT", DEFAULT_PALINDROME_TEXT),
        help="Text used for Manacher longest-palindrome output.",
    )
    parser.add_argument(
        "--comparison-text",
        default=os.getenv("STRING_ALGORITHMS_COMPARISON_TEXT", DEFAULT_COMPARISON_TEXT),
        help="Second text used for sequence-comparison algorithms.",
    )
    parser.add_argument(
        "--benchmark",
        action="store_true",
        default=os.getenv("STRING_ALGORITHMS_BENCHMARK", "false").lower() == "true",
        help="Print a simple timing snapshot.",
    )
    config = parser.parse_args(argv)
    config.text = _require_non_blank(config.text, "text")
    config.pattern = _require_non_blank(config.pattern, "pattern")
    config.palindrome_text = _require_non_blank(
        config.palindrome_text, "palindrome text"
    )
    config.comparison_text = _require_non_blank(
        config.comparison_text, "comparison text"
    )
    config.patterns = _parse_patterns(config.patterns)
    return config


def _parse_patterns(value: str) -> list[str]:
    patterns = [pattern.strip() for pattern in value.split(",") if pattern.strip()]
    if not patterns:
        raise ValueError("patterns must contain at least one value")
    return patterns


def _require_non_blank(value: str, name: str) -> str:
    if value is None or value.strip() == "":
        raise ValueError(f"{name} must not be blank")
    return value
