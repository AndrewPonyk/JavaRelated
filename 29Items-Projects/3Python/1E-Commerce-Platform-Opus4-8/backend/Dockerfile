# syntax=docker/dockerfile:1
# Multi-stage build keeps the runtime image free of build/test tooling.

FROM python:3.12-slim AS base
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# --- Dependencies layer (cached unless requirements change) ---------------
FROM base AS deps
COPY requirements/ requirements/
RUN pip install --prefix=/install -r requirements/production.txt

# --- Final runtime image ---------------------------------------------------
FROM base AS runtime
COPY --from=deps /install /usr/local
COPY . .

# Run as non-root.
RUN useradd --create-home appuser && chown -R appuser /app
USER appuser

EXPOSE 8000

# Container health probe (no curl in slim image -> use stdlib urllib).
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8000/healthz').status==200 else 1)" || exit 1

# Gunicorn for sync; swap to uvicorn workers if serving async views.
CMD ["gunicorn", "config.wsgi:application", \
     "--bind", "0.0.0.0:8000", "--workers", "4", "--timeout", "60"]
