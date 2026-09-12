"""Shared pytest fixtures.

Environment is configured BEFORE importing the app so the DB engine binds to an
in-memory SQLite database and Redis is disabled. The fake ONNX session lets us
exercise the full inference + API path without an ONNX model or a real Redis server.
"""

from __future__ import annotations

import io
import os

# --- Must run before any `app.*` import (engine + settings are built at import) ---
os.environ["DATABASE_URL"] = "sqlite://"  # in-memory, shared via StaticPool
os.environ["REDIS_URL"] = ""
os.environ["API_KEYS"] = ""  # open dev mode for most tests
os.environ["APP_ENV"] = "development"
os.environ["JWT_SECRET"] = "test-secret"
os.environ["RATE_LIMIT_ENABLED"] = "false"

import numpy as np  # noqa: E402
import pytest  # noqa: E402
from app.db.session import init_db  # noqa: E402
from app.main import create_app  # noqa: E402
from app.services.cache import CacheService  # noqa: E402
from app.services.inference import InferenceService  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from PIL import Image  # noqa: E402


class FakeOnnxSession:
    """Returns deterministic logits regardless of input."""

    def __init__(self, logits: np.ndarray):
        self._logits = logits

    def run(self, output_names, input_feed):  # noqa: ARG002 - signature parity
        batch = next(iter(input_feed.values())).shape[0]
        return [np.repeat(self._logits[np.newaxis, :], batch, axis=0)]


@pytest.fixture
def labels() -> list[str]:
    return ["electronics", "clothing", "home"]


@pytest.fixture
def inference_service(labels: list[str]) -> InferenceService:
    # logits -> sigmoid: electronics high, clothing mid, home low.
    logits = np.array([4.0, 0.5, -4.0], dtype=np.float32)
    return InferenceService(
        session=FakeOnnxSession(logits),
        labels=labels,
        thresholds=[0.5, 0.5, 0.5],
        model_version="test-v1",
    )


@pytest.fixture
def cache_service() -> CacheService:
    return CacheService(client=None, ttl_seconds=60, model_version="test-v1")


@pytest.fixture
def client(inference_service, cache_service) -> TestClient:
    app = create_app()
    with TestClient(app) as c:
        # Replace lifespan-loaded singletons with test doubles.
        c.app.state.inference_service = inference_service
        c.app.state.cache_service = cache_service
        init_db()
        yield c


@pytest.fixture
def sample_image_bytes() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (256, 256), color=(123, 222, 64)).save(buf, format="JPEG")
    return buf.getvalue()


@pytest.fixture
def png_bytes() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (64, 64), color=(10, 20, 200)).save(buf, format="PNG")
    return buf.getvalue()
