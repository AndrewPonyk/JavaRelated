"""Cross-cutting utilities: error envelope, middleware, logging, reporting."""

import json
import logging
from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest

from common.utils.exceptions import (
    DomainError,
    InventoryInsufficientError,
    standard_exception_handler,
)
from common.utils.logging import JSONFormatter


def test_domain_error_envelope():
    exc = InventoryInsufficientError("nope", details={"available": 0})
    request = SimpleNamespace(request_id="abc123")
    resp = standard_exception_handler(exc, {"request": request})
    assert resp.status_code == 409
    assert resp.data["error"]["code"] == "INVENTORY_INSUFFICIENT"
    assert resp.data["error"]["request_id"] == "abc123"
    assert resp.data["error"]["details"] == {"available": 0}


def test_drf_error_reshaped(rf):
    from rest_framework.exceptions import ValidationError

    request = SimpleNamespace(request_id="r1")
    resp = standard_exception_handler(ValidationError({"name": ["required"]}), {"request": request})
    assert resp.status_code == 400
    assert "name" in resp.data["error"]["details"]


def test_unhandled_exception_returns_none():
    # Non-API exceptions are not reshaped (fall through to Django -> 500).
    assert standard_exception_handler(KeyError("x"), {"request": None}) is None


def test_base_domain_error_defaults():
    exc = DomainError("bad")
    assert exc.status_code == 400 and exc.code == "DOMAIN_ERROR"


def test_json_formatter_includes_extra():
    formatter = JSONFormatter()
    record = logging.LogRecord("test", logging.INFO, __file__, 1, "hello", None, None)
    record.request_id = "xyz"
    payload = json.loads(formatter.format(record))
    assert payload["message"] == "hello"
    assert payload["request_id"] == "xyz"
    assert payload["level"] == "INFO"


@pytest.mark.django_db
def test_request_id_middleware_sets_header(client):
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert "X-Request-ID" in resp.headers


def test_reporting_top_selling(mocker):
    """Reporting query maps SQLAlchemy rows to dicts (engine mocked)."""
    from common.utils import reporting

    row = SimpleNamespace(_mapping={"product_id": 1, "units_sold": 5, "revenue": 50})
    conn = MagicMock()
    conn.__enter__.return_value = conn
    conn.execute.return_value = [row]
    engine = MagicMock()
    engine.connect.return_value = conn
    mocker.patch("common.utils.reporting.get_read_engine", return_value=engine)

    result = reporting.top_selling_products(limit=5)
    assert result == [{"product_id": 1, "units_sold": 5, "revenue": 50}]
