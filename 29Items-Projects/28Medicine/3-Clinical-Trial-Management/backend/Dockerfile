# syntax=docker/dockerfile:1
# Multi-stage build: compile deps in a builder, run on a slim non-root runtime.
# One image runs web / worker / beat via the command override (TECH-NOTES §3.3).

# ── Builder ────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS builder
ENV PIP_NO_CACHE_DIR=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*
COPY requirements/ requirements/
RUN python -m venv /opt/venv \
    && /opt/venv/bin/pip install --upgrade pip \
    && /opt/venv/bin/pip install -r requirements/prod.txt

# ── Runtime ────────────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/opt/venv/bin:$PATH" \
    DJANGO_SETTINGS_MODULE=config.settings.production
WORKDIR /app

# Non-root user — never run the app as root.
RUN useradd --create-home --uid 10001 appuser
COPY --from=builder /opt/venv /opt/venv
COPY --chown=appuser:appuser . .

USER appuser
EXPOSE 8000

# Liveness probe hits the no-DB health endpoint.
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/healthz/').status==200 else 1)" || exit 1

# Default = web. Override in ECS task def for worker/beat:
#   worker: celery -A config worker -Q default,ml,notifications,reports -l info
#   beat:   celery -A config beat -l info
CMD ["gunicorn", "config.wsgi:application", "--bind", "0.0.0.0:8000", "--workers", "4"]
