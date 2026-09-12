# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Stage 1 — builder: resolve dependencies with Poetry and build wheels
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS builder

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    POETRY_NO_INTERACTION=1 \
    POETRY_VIRTUALENVS_CREATE=false

RUN pip install "poetry==1.8.3"

WORKDIR /app

# poetry.lock is copied when present (generate with `poetry lock`); the glob
# keeps the build working for a fresh scaffold that has no lock file yet.
COPY pyproject.toml poetry.lock* ./

RUN if [ ! -f poetry.lock ]; then poetry lock; fi \
    && poetry export --without-hashes --only main -f requirements.txt -o requirements.txt

RUN pip wheel --no-cache-dir --wheel-dir /wheels -r requirements.txt

# ---------------------------------------------------------------------------
# Stage 2 — runtime: slim image, non-root, wheels only (no compilers, no Poetry)
# ---------------------------------------------------------------------------
FROM python:3.12-slim AS runtime

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONPATH=/app/src \
    FRAUD_MODEL_DIR=/app/models

RUN useradd --create-home --shell /usr/sbin/nologin appuser

WORKDIR /app

COPY --from=builder /wheels /wheels
COPY --from=builder /app/requirements.txt ./
RUN pip install --no-index --find-links=/wheels -r requirements.txt \
    && rm -rf /wheels requirements.txt

COPY src/ ./src/
COPY migrations/ ./migrations/
COPY scripts/entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh \
    && mkdir -p /app/models \
    && chown -R appuser:appuser /app/models

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health/live', timeout=3)" || exit 1

ENTRYPOINT ["./entrypoint.sh"]
