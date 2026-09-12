FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt backend/requirements-dev.txt ./backend/
RUN pip install --upgrade pip && \
    pip install -r backend/requirements.txt -r backend/requirements-dev.txt uvicorn fastapi

COPY backend/ /app/backend/
COPY migrations/ /app/migrations/

WORKDIR /app/backend
ENV PYTHONPATH=/app/backend:/app

EXPOSE 8000

CMD ["uvicorn", "src.server:app", "--host", "0.0.0.0", "--port", "8000"]
