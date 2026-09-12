# syntax=docker/dockerfile:1
# Multi-stage build. Final image installs ONLY runtime deps (no torch/transformers)
# to stay small and cold-start fast on Cloud Run.

# ---- Builder ----
FROM python:3.12-slim AS builder
ENV PYTHONDONTWRITEBYTECODE=1 PIP_NO_CACHE_DIR=1
WORKDIR /build

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

# ---- Runtime ----
FROM python:3.12-slim AS runtime
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/opt/venv/bin:$PATH" \
    PORT=8080

# Non-root user
RUN useradd --create-home --uid 10001 appuser
WORKDIR /app

COPY --from=builder /opt/venv /opt/venv
COPY app ./app
COPY alembic.ini ./alembic.ini
COPY migrations ./migrations

# Model artifacts are mounted/baked at /models (see MODEL_PATH).
USER appuser
EXPOSE 8080

# Honor Cloud Run's $PORT. Single worker keeps one ONNX session per instance;
# scale via Cloud Run instances + container concurrency instead.
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
