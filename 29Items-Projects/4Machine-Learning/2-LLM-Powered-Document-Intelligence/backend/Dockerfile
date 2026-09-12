# syntax=docker/dockerfile:1
# Multi-stage build: install deps in a builder, copy into a slim runtime, run non-root.
# The same image runs the API and the ingestion worker (different entrypoints).
#
# Build the default (local backend, lean):   docker build -t docintel-api .
# Build with the Bedrock/Pinecone stack:      docker build --build-arg RAG_EXTRAS=bedrock -t docintel-api .

FROM python:3.12-slim AS builder
WORKDIR /app
ENV PIP_NO_CACHE_DIR=1 PYTHONDONTWRITEBYTECODE=1
ARG RAG_EXTRAS=""
COPY requirements.txt requirements-bedrock.txt ./
RUN pip install --prefix=/install -r requirements.txt && \
    if [ "$RAG_EXTRAS" = "bedrock" ]; then pip install --prefix=/install -r requirements-bedrock.txt; fi

FROM python:3.12-slim AS runtime
WORKDIR /app
ENV PYTHONUNBUFFERED=1 PYTHONPATH=/app
# Non-root user for least privilege.
RUN useradd --create-home --uid 10001 appuser
COPY --from=builder /install /usr/local
COPY . .
USER appuser
EXPOSE 8000
# API entrypoint. The worker overrides CMD: python -m app.worker
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
