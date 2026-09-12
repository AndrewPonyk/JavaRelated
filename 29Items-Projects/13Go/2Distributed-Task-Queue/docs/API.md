# Distributed Task Queue API

All `/api/v1` routes require `X-API-Key`.

## Routes

- `POST /api/v1/tasks`: create a task.
- `GET /api/v1/tasks?status=&limit=`: list tasks.
- `GET /api/v1/tasks/{id}`: fetch one task.
- `PUT /api/v1/tasks/{id}`: update a pending or retrying task.
- `POST /api/v1/tasks/{id}/cancel`: cancel a task.
- `DELETE /api/v1/tasks/{id}`: delete a task record.
- `GET /api/v1/tasks/{id}/attempts`: list attempt audit entries.
- `GET /api/v1/dead-letters`: list dead-lettered tasks.
- `POST /api/v1/dead-letters/{id}/requeue`: requeue a dead-lettered task.
- `GET /api/v1/queue/stats`: inspect Redis queue depth.
- `GET /healthz`: health check.
- `GET /metrics`: Prometheus metrics.

## Examples

Create a task:

```sh
curl -s -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "X-API-Key: replace-with-local-api-key" \
  -d '{"type":"example.email","payload":{"recipient":"user@example.com"},"max_attempts":3,"timeout_seconds":30}'
```

List tasks:

```sh
curl -s http://localhost:8080/api/v1/tasks?status=pending \
  -H "X-API-Key: replace-with-local-api-key"
```

Cancel a task:

```sh
curl -s -X POST http://localhost:8080/api/v1/tasks/00000000-0000-4000-8000-000000000000/cancel \
  -H "X-API-Key: replace-with-local-api-key"
```

Inspect queue stats:

```sh
curl -s http://localhost:8080/api/v1/queue/stats \
  -H "X-API-Key: replace-with-local-api-key"
```

Responses use JSON. Validation errors return `400`, authentication failures return `401`, missing records return `404`, conflicts return `409`, rate limit failures return `429`, and unexpected server errors return `500`.

## Task Types

- `example.email`: payload requires `recipient`.
- `example.webhook`: payload requires absolute `url`.
- `example.report`: payload requires `name`.
- `example.fail_once`: fails first attempt and succeeds on retry.
- `example.always_fail`: retries until max attempts and then moves to dead letters.
