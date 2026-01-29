# Backend Dockerfile for E-Commerce Platform
# Multi-stage build for smaller production images

# Stage 1: Builder
FROM python:3.12-slim as builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies (including dev for testing)
# Use CPU-only PyTorch for much faster builds (~200MB vs ~2.5GB)
COPY backend/requirements.txt backend/requirements-dev.txt ./
RUN pip wheel --no-cache-dir --no-deps --wheel-dir /app/wheels \
    --extra-index-url https://download.pytorch.org/whl/cpu \
    -r requirements-dev.txt

# Stage 2: Runtime
FROM python:3.12-slim as runtime

WORKDIR /app

# Create non-root user
RUN groupadd -r django && useradd -r -g django django

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    curl \
    netcat-traditional \
    && rm -rf /var/lib/apt/lists/*

# Copy wheels from builder and install
COPY --from=builder /app/wheels /wheels
RUN pip install --no-cache --extra-index-url https://download.pytorch.org/whl/cpu /wheels/*

# Copy application code
COPY backend/ .

# Copy entrypoint script
COPY docker/backend/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create directories for static and media files
RUN mkdir -p /app/staticfiles /app/media

# Change ownership
RUN chown -R django:django /app

# Switch to non-root user
USER django

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8000/health/ || exit 1

# Set entrypoint
ENTRYPOINT ["/entrypoint.sh"]

# Default command
CMD ["gunicorn", "--bind", "0.0.0.0:8000", "--workers", "4", "--threads", "2", "core.wsgi:application"]
