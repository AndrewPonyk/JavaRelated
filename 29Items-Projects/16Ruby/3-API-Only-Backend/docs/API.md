# API Documentation

The API returns JSON and uses a consistent error structure:

```json
{ "error": { "code": "validation_error", "message": ["Email is invalid"] } }
```

## Authentication

JWTs are signed with `JWT_SECRET_KEY`, include `sub`, `email`, `iat`, `exp`, and `jti` claims, and are revoked on logout through the `revoked_tokens` table.

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | No | Create a user and issue a token |
| POST | `/api/v1/auth/login` | No | Authenticate and issue a token |
| DELETE | `/api/v1/auth/logout` | Yes | Revoke the current token |
| GET | `/api/v1/auth/me` | Yes | Return current user |

## Resources

| Resource | CRUD paths |
| --- | --- |
| Users | `/api/v1/users`, `/api/v1/users/:id` |
| Sync items | `/api/v1/sync_items`, `/api/v1/sync_items/:id` |
| Metrics | `/api/v1/metrics`, `/api/v1/metrics/:id` |
| Anomalies | `/api/v1/anomalies`, `/api/v1/anomalies/:id`, `/api/v1/anomalies/:id/resolve` |

Collection endpoints accept `page` and `per_page` query parameters. `per_page` is capped at 100. Responses include pagination metadata:

```json
{
  "meta": {
    "page": 1,
    "per_page": 25,
    "total_count": 42,
    "total_pages": 2
  }
}
```

## Sync Contract

`POST /api/v1/sync` accepts an array of client changes. Each change must include `collection_name`, `record_id`, and an object `payload`. The server applies newer client changes and returns HTTP `409` with conflict details when the server has a newer version.

`GET /api/v1/sync/status` returns:

```json
{
  "lastSyncAt": "2026-05-04T12:00:00Z",
  "status": "success",
  "totalRecords": 3,
  "deletedRecords": 0
}
```

## Metrics and Anomaly Detection

Every controller request is recorded as an `ApiMetric` with method, path, status, latency, timestamp, and request metadata. `AnomalyDetectionJob` runs through Sidekiq and creates open anomalies when recent error rate or average latency exceeds configured service thresholds.
