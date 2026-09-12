"""Shared text-normalization helpers.

`collapse_whitespace` preserves case (used for the user-facing ``normalized_query``
echoed back in responses), while `normalize_key` lowercases as well and is what we
store in ``addresses.normalized_address`` and compare against for trigram search.
"""


def collapse_whitespace(value: str) -> str:
    return " ".join(value.split())


def normalize_key(value: str) -> str:
    return collapse_whitespace(value).lower()
