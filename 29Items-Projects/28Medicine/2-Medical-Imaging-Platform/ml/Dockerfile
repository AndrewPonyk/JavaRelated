# For GPU inference, switch the base to an NVIDIA CUDA runtime image and install
# the matching torch build. CPU image shown for the stub.
FROM python:3.12-slim AS runtime

ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home mluser

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY inference ./inference

USER mluser
EXPOSE 8001
HEALTHCHECK CMD python -c "import httpx,sys; sys.exit(0 if httpx.get('http://localhost:8001/health/live').status_code==200 else 1)"
CMD ["uvicorn", "inference.server:app", "--host", "0.0.0.0", "--port", "8001"]
