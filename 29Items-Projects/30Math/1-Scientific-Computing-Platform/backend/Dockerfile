# Backend image (API and worker share it; the command differs).
# Build from the REPO ROOT so the uv workspace (libs/) is in context:
#   docker build -f backend/Dockerfile -t scp-backend:dev .
#
# API:    uvicorn app.main:app
# Worker: celery -A app.workers.celery_app worker -Q compute

FROM python:3.12-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy

WORKDIR /workspace
COPY pyproject.toml uv.lock ./
COPY libs/ libs/
COPY backend/pyproject.toml backend/pyproject.toml
# Dependency layer first (cache-friendly): everything except our own packages.
RUN uv sync --frozen --package scp-backend --no-dev --no-install-workspace
COPY backend/app backend/app
COPY backend/migrations backend/migrations
COPY backend/alembic.ini backend/alembic.ini
RUN uv sync --frozen --package scp-backend --no-dev

FROM python:3.12-slim

# Non-root runtime user (docs/ARCHITECTURE.md §2.5, supply chain)
RUN useradd --create-home --uid 10001 scp
# Artifact volume mount point must be writable by the runtime user; a fresh
# named volume inherits this ownership on first mount.
RUN mkdir -p /data/artifacts && chown scp:scp /data/artifacts
WORKDIR /workspace/backend

COPY --from=builder --chown=scp:scp /workspace /workspace

ENV PATH="/workspace/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    OMP_NUM_THREADS=1 \
    OPENBLAS_NUM_THREADS=1

# Warm the Matplotlib font cache at build time, not on first request
RUN python -c "import matplotlib.figure" || true

USER scp
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=2)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
