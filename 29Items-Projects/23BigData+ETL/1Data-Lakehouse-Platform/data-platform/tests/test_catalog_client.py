"""Catalog self-registration client — fast tests with a faked HTTP layer."""

from __future__ import annotations

import io
import json
import urllib.error
import urllib.request

import pytest

from lakehouse.common.catalog import register_dataset_version
from lakehouse.common.config import LakehouseSettings


def _settings(url: str = "http://catalog.local") -> LakehouseSettings:
    return LakehouseSettings(
        bronze_uri="s3a://b",
        silver_uri="s3a://s",
        gold_uri="s3a://g",
        artifacts_uri="s3a://a",
        kafka_bootstrap_servers="k:9092",
        orders_topic="orders.v1",
        dlq_topic="orders.v1.dlq",
        s3_endpoint=None,
        local_mode=False,
        catalog_api_url=url,
    )


class FakeResponse(io.BytesIO):
    def __init__(self, status: int, body: dict):
        super().__init__(json.dumps(body).encode())
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


@pytest.fixture()
def http_calls(monkeypatch):
    """Capture requests; respond from a scripted queue."""
    calls: list[tuple[str, str, dict | None]] = []
    responses: list[FakeResponse | Exception] = []

    def fake_urlopen(request, timeout=None):
        payload = json.loads(request.data) if request.data else None
        calls.append((request.get_method(), request.full_url, payload))
        result = responses.pop(0)
        if isinstance(result, Exception):
            raise result
        return result

    monkeypatch.setattr(urllib.request, "urlopen", fake_urlopen)
    return calls, responses


def test_disabled_when_no_url():
    ok = register_dataset_version(
        _settings(url=""), name="x.y", layer="gold", s3_path="s3://b/x", schema={"a": "string"}
    )
    assert ok is False


def test_registers_new_dataset_and_version(http_calls):
    calls, responses = http_calls
    responses.append(FakeResponse(201, {"id": "d-1"}))
    responses.append(FakeResponse(201, {"version": 1}))

    ok = register_dataset_version(
        _settings(),
        name="sales.orders",
        layer="silver",
        s3_path="s3://b/sales/orders",
        schema={"order_id": "string"},
        row_count=42,
    )

    assert ok is True
    assert calls[0][0] == "POST" and calls[0][1].endswith("/api/v1/datasets")
    assert calls[1][1].endswith("/api/v1/datasets/d-1/versions")
    assert calls[1][2] == {"schema_json": {"order_id": "string"}, "row_count": 42}


def test_conflict_falls_back_to_lookup(http_calls):
    calls, responses = http_calls
    responses.append(
        urllib.error.HTTPError("u", 409, "conflict", {}, io.BytesIO(b'{"detail":"exists"}'))
    )
    responses.append(FakeResponse(200, {"items": [{"id": "d-9"}], "total": 1}))
    responses.append(FakeResponse(201, {"version": 7}))

    ok = register_dataset_version(
        _settings(),
        name="sales.orders",
        layer="silver",
        s3_path="s3://b/sales/orders",
        schema={"order_id": "string"},
    )

    assert ok is True
    assert calls[1][0] == "GET" and "name=sales.orders" in calls[1][1]
    assert calls[2][1].endswith("/api/v1/datasets/d-9/versions")


def test_unreachable_api_is_swallowed(http_calls):
    _, responses = http_calls
    responses.append(urllib.error.URLError("connection refused"))

    ok = register_dataset_version(
        _settings(),
        name="sales.orders",
        layer="silver",
        s3_path="s3://b/x",
        schema={"a": "string"},
    )
    assert ok is False  # logged, never raised


def test_token_attached_when_configured(monkeypatch):
    seen = {}

    def fake_urlopen(request, timeout=None):
        seen["auth"] = request.headers.get("Authorization")
        return FakeResponse(201, {"id": "d-1"})

    monkeypatch.setattr(urllib.request, "urlopen", fake_urlopen)

    settings = LakehouseSettings(
        bronze_uri="s3a://b",
        silver_uri="s3a://s",
        gold_uri="s3a://g",
        artifacts_uri="s3a://a",
        kafka_bootstrap_servers="k:9092",
        orders_topic="o",
        dlq_topic="d",
        s3_endpoint=None,
        local_mode=False,
        catalog_api_url="http://catalog.local",
        catalog_api_token="secret-token",
    )
    # Only the first call matters for the header assertion; the fake raises
    # IndexError once its scripted responses run out.
    import contextlib

    with contextlib.suppress(IndexError):
        register_dataset_version(
            settings, name="a.b", layer="gold", s3_path="s3://b/a", schema={"a": "string"}
        )
    assert seen["auth"] == "Bearer secret-token"
