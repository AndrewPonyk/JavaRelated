"""Health/readiness probe tests (liveness vs readiness — TECH-NOTES §3.3)."""
from __future__ import annotations

import pytest
from rest_framework.test import APIClient


def test_healthz_is_public_and_does_not_touch_db():
    resp = APIClient().get("/healthz/")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


@pytest.mark.django_db
def test_readyz_reports_database_up():
    resp = APIClient().get("/readyz/")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["database"] == "up"
