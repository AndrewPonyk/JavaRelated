"""Event logging: normalization and the never-break-serving guarantee."""

import uuid

from app.services.event_service import EventService, normalize_query


def test_normalize_query_lowercases_and_collapses_whitespace() -> None:
    assert normalize_query("  Wireless   HEADPHONES ") == "wireless headphones"
    assert normalize_query("a" * 1000) == "a" * 512  # capped to column length


async def test_write_failures_are_swallowed() -> None:
    def broken_factory():  # type: ignore[no-untyped-def]
        raise ConnectionError("pg down")

    service = EventService(session_factory=broken_factory)
    # Must not raise — losing a log line never fails a request.
    await service.log_click(query="tv", product_id=uuid.uuid4(), position=1, session_id="s1")
    await service.log_search(
        query="tv", filters={}, results_count=0, shown_product_ids=[], session_id="s1"
    )
