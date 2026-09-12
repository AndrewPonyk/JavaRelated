from string_algorithms.algorithms import (
    AhoCorasick,
    SuffixArray,
    SuffixTree,
    Trie,
    kmp_search,
    longest_common_subsequence,
    longest_common_subsequence_length,
    longest_common_substring,
    longest_common_substring_length,
    longest_palindrome,
    rabin_karp_search,
)
from string_algorithms.demo.runner import run
from string_algorithms.__main__ import main

import pytest


def test_finds_overlapping_matches() -> None:
    assert kmp_search("aaaa", "aa") == [0, 1, 2]
    assert rabin_karp_search("aaaa", "aa") == [0, 1, 2]


def test_returns_no_matches_when_pattern_is_missing() -> None:
    assert kmp_search("abcdef", "xyz") == []
    assert rabin_karp_search("abcdef", "xyz") == []
    assert SuffixArray("abcdef").search("xyz") == []
    assert not SuffixTree("abcdef").contains("xyz")


def test_validates_empty_patterns() -> None:
    with pytest.raises(ValueError):
        kmp_search("abc", "")
    with pytest.raises(ValueError):
        rabin_karp_search("abc", "")
    with pytest.raises(ValueError):
        SuffixArray("abc").search("")
    with pytest.raises(ValueError):
        AhoCorasick([""])


def test_supports_multiple_pattern_matching() -> None:
    matcher = AhoCorasick(["he", "she", "hers"])
    assert len(matcher.search("ushers")) == 3


def test_supports_suffix_based_searches() -> None:
    assert SuffixArray("banana").search("ana") == [1, 3]
    suffix_tree = SuffixTree("banana")
    assert suffix_tree.contains("nan")
    assert suffix_tree.edge_count > 0


def test_supports_palindrome_and_trie_lookups() -> None:
    assert longest_palindrome("forgeeksskeegfor") == "geeksskeeg"

    trie = Trie()
    trie.insert("pattern")
    assert trie.contains("pattern")
    assert trie.starts_with("pat")


def test_supports_sequence_comparison_algorithms() -> None:
    assert longest_common_subsequence("AGGTAB", "GXTXAYB") == "GTAB"
    assert longest_common_subsequence_length("AGGTAB", "GXTXAYB") == 4
    assert longest_common_substring("ABABC", "BABCA") == "BABC"
    assert longest_common_substring_length("ABABC", "BABCA") == 4
    assert longest_common_subsequence("", "abc") == ""
    assert longest_common_substring("abc", "xyz") == ""


def test_handles_empty_text_and_unicode_samples() -> None:
    assert kmp_search("", "abc") == []
    assert rabin_karp_search("", "abc") == []
    assert kmp_search("caf\u00e9 caf\u00e9", "caf\u00e9") == [0, 5]
    assert rabin_karp_search("caf\u00e9 caf\u00e9", "caf\u00e9") == [0, 5]
    assert longest_palindrome("bananas") == "anana"


def test_console_demo_accepts_custom_arguments(capsys: pytest.CaptureFixture[str]) -> None:
    run(
        [
            "--text",
            "mississippi",
            "--pattern",
            "issi",
            "--patterns",
            "is,ssi,miss",
            "--palindrome-text",
            "abacdfgdcaba",
            "--comparison-text",
            "missouri",
        ]
    )

    output = capsys.readouterr().out
    assert "KMP matches for 'issi': [1, 4]" in output
    assert "Aho-Corasick matches:" in output
    assert "Manacher longest palindrome" in output
    assert "Longest Common Subsequence" in output
    assert "Longest Common Substring" in output


def test_main_returns_error_code_for_invalid_arguments(
    capsys: pytest.CaptureFixture[str],
) -> None:
    assert main(["--pattern", ""]) == 2
    assert "pattern must not be blank" in capsys.readouterr().err
