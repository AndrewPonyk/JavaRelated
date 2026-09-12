"""Template mining + PII redaction (common/parsing.py — shared with the Spark job)."""

from __future__ import annotations

from log_analytics.common.parsing import redact, template_id, template_of


class TestTemplateOf:
    def test_masks_numbers(self) -> None:
        assert template_of("order 1234 failed after 56.7ms") == "order <num> failed after <num>ms"

    def test_groups_equivalent_messages(self) -> None:
        assert template_of("order 1234 failed") == template_of("order 99 failed")

    def test_masks_uuids_and_hex_ids(self) -> None:
        templated = template_of("trace 550e8400-e29b-41d4-a716-446655440000 span deadbeefdeadbeef")
        assert "<uuid>" in templated
        assert "<hex>" in templated

    def test_masks_quoted_strings(self) -> None:
        assert template_of("user 'alice' not found") == "user <str> not found"

    def test_collapses_whitespace_and_truncates(self) -> None:
        assert template_of("a    b\t\tc") == "a b c"
        assert len(template_of("x" * 500)) == 200


class TestTemplateId:
    def test_stable_and_16_hex_chars(self) -> None:
        a = template_id("order 1 failed")
        b = template_id("order 2 failed")
        assert a == b
        assert len(a) == 16
        int(a, 16)  # hex-parseable

    def test_different_templates_differ(self) -> None:
        assert template_id("order failed") != template_id("payment succeeded")


class TestRedact:
    def test_emails(self) -> None:
        assert redact("contact andrii.p@example.com now") == "contact <email> now"

    def test_bearer_tokens(self) -> None:
        assert redact("auth Bearer eyJhbGciOiJIUzI1NiJ9.payload") == "auth bearer <redacted>"

    def test_key_value_secrets(self) -> None:
        assert redact("retry with password=hunter2 ok") == "retry with password=<redacted> ok"
        assert redact("API_KEY: abc123!") == "API_KEY=<redacted>"

    def test_normal_messages_untouched(self) -> None:
        msg = "request completed status=200 duration_ms=12"
        assert redact(msg) == msg
