# Deliberately vulnerable e2e target — never deployed, e2e compose profile only.
FROM python:3.12-alpine

WORKDIR /app
COPY app.py .

EXPOSE 8080
CMD ["python", "app.py"]
