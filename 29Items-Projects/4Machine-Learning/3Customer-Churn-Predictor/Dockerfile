# Optional image for self-hosting / local parity. Streamlit Cloud does NOT use this.
FROM python:3.12-slim AS base

# System deps occasionally needed by lightgbm/catboost wheels.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
 && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Install deps first for better layer caching.
COPY requirements.txt pyproject.toml ./
RUN pip install --upgrade pip && pip install -r requirements.txt

# Copy source and install the package.
COPY src ./src
COPY app ./app
COPY migrations ./migrations
COPY README.md ./README.md
COPY .streamlit ./.streamlit
RUN pip install -e .

EXPOSE 8501

# curl-free healthcheck (slim image has no curl).
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:8501/_stcore/health').status==200 else 1)" || exit 1

CMD ["streamlit", "run", "app/streamlit_app.py", "--server.port=8501", "--server.address=0.0.0.0"]
