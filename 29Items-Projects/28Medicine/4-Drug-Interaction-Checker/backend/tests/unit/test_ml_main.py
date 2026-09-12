"""Tests for the standalone ML service app."""

from fastapi.testclient import TestClient

import app.ml_main as ml_main
from app.ml.model import SeverityModel


def test_health():
    SeverityModel.reset()
    with TestClient(ml_main.app) as client:
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "ok"


def test_predict(monkeypatch):
    async def fake_predict(a, b):
        return {
            "rxcui_a": a,
            "rxcui_b": b,
            "severity": "major",
            "confidence": 0.9,
            "mechanism": None,
        }

    monkeypatch.setattr(ml_main, "predict_severity", fake_predict)
    with TestClient(ml_main.app) as client:
        resp = client.post("/predict", json={"rxcui_a": "1", "rxcui_b": "2"})
        assert resp.status_code == 200
        assert resp.json()["severity"] == "major"


def test_predict_batch(monkeypatch):
    async def fake_batch(pairs):
        return [
            {"rxcui_a": a, "rxcui_b": b, "severity": "minor", "confidence": 0.6, "mechanism": None}
            for a, b in pairs
        ]

    monkeypatch.setattr(ml_main, "predict_severity_batch", fake_batch)
    with TestClient(ml_main.app) as client:
        resp = client.post("/predict/batch", json={"pairs": [{"rxcui_a": "1", "rxcui_b": "2"}]})
        assert resp.status_code == 200
        assert len(resp.json()["predictions"]) == 1
