FROM python:3.12-slim

WORKDIR /workspace

COPY pyproject.toml README.md ./
COPY src/python ./src/python
COPY tests ./tests
COPY data ./data

RUN python -m pip install --no-cache-dir -e ".[dev]"

CMD ["sh", "-c", "python -m geometry.cli.main --input data/fixtures/default_points.csv && pytest"]
