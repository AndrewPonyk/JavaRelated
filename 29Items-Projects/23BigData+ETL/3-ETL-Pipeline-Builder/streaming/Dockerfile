FROM python:3.12-slim

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY processor/ processor/

RUN useradd --system --no-create-home processor
USER processor

# SIGTERM → graceful drain: finish batch, commit offsets, exit (main.py TODO)
CMD ["python", "-m", "processor.main"]
