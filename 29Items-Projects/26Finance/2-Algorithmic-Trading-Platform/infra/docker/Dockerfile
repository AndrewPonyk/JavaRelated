# Single image for all Python control-plane services. The compose `command`
# selects which one to run. Services import each other via PYTHONPATH (the same
# layout the test suite uses), so there is no per-service build.
FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /app

# Install runtime deps first for layer caching.
# Generous timeout + retries so the build survives slow/flaky networks.
COPY infra/docker/requirements-runtime.txt /tmp/requirements-runtime.txt
RUN pip install --no-cache-dir --prefer-binary \
        --default-timeout=300 --retries 10 \
        -r /tmp/requirements-runtime.txt

# Copy source (shared lib + all services + config).
COPY shared/ /app/shared/
COPY services/ /app/services/
COPY config/ /app/config/
COPY db/ /app/db/
COPY scripts/ /app/scripts/

ENV PYTHONPATH=/app/shared:/app/services/api-gateway/src:/app/services/strategy-engine/src:/app/services/risk-engine/src:/app/services/market-data/src:/app/services/ml-models/src:/app/services/backtesting/src:/app/services/pnl-projector/src:/app/services/orchestrator/src

# Non-root user.
RUN useradd --create-home --uid 10001 trading
USER trading

# Default: the API gateway. Overridden per-service in docker-compose.
EXPOSE 8000
CMD ["uvicorn", "api_gateway.asgi:app", "--host", "0.0.0.0", "--port", "8000"]
