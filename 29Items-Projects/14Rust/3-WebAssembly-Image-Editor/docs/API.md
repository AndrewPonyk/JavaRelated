# API reference

Base path: `/api`. All JSON responses use `content-type: application/json` and `cache-control: no-store`. Errors use:

```json
{
  "error": { "code": "INVALID_INPUT", "message": "Request validation failed.", "requestId": "uuid" }
}
```

## Session

`POST /auth/anonymous`

Body (optional): `{ "displayName": "Anonymous editor" }`. The display name is 1-60 characters.

Returns `201` with `{ "data": { "user": { "id", "displayName", "createdAt" }, "token": "signed-jwt" } }`. Send `Authorization: Bearer <token>` for all routes below.

## Health

`GET /health` checks database connectivity and returns `200` with `{ "data": { "status": "ok" } }` without authentication. It returns `503` while the database is unavailable.

## Pagination

Project and preset collection routes accept `limit` (default 50, range 1-100) and `offset` (default 0, range 0-10,000). Collection responses include page metadata:

```json
{
  "data": [],
  "meta": { "limit": 50, "offset": 0, "count": 0 }
}
```

`count` is the number of rows in the current page. Request the next offset until `count < limit`.

## Projects

| Method | Path                          | Body                       | Result                    |
| ------ | ----------------------------- | -------------------------- | ------------------------- |
| GET    | `/projects?limit=50&offset=0` | none                       | Owner's project page      |
| POST   | `/projects`                   | `{ "name": "My project" }` | Created project, `201`    |
| GET    | `/projects/:id`               | none                       | One owner-visible project |
| PUT    | `/projects/:id`               | `{ "name": "Renamed" }`    | Updated project           |
| DELETE | `/projects/:id`               | none                       | `204`                     |

Project names are trimmed strings of 1-120 characters.

## Presets

| Method | Path                                         | Body                   | Result                          |
| ------ | -------------------------------------------- | ---------------------- | ------------------------------- |
| GET    | `/presets?limit=50&offset=0`                 | none                   | Owner's preset page             |
| GET    | `/presets?projectId=:uuid&limit=50&offset=0` | none                   | Owner's presets for one project |
| POST   | `/presets`                                   | preset payload         | Created preset, `201`           |
| GET    | `/presets/:id`                               | none                   | One owner-visible preset        |
| PUT    | `/presets/:id`                               | partial preset payload | Updated preset                  |
| DELETE | `/presets/:id`                               | none                   | `204`                           |

A preset payload contains `name` (1-80 characters), optional `projectId` UUID or `null`, and a strict `recipe`:

```json
{
  "name": "Warm portrait",
  "projectId": null,
  "recipe": {
    "schemaVersion": 1,
    "filters": [
      { "name": "sepia", "amount": 0 },
      { "name": "contrast", "amount": 12 }
    ],
    "crop": { "x": 0.1, "y": 0.1, "width": 0.8, "height": 0.8 }
  }
}
```

Fixed filters use amount `0`; brightness, contrast, and saturation accept `-100..100`; blur accepts `1..8`; sharpen accepts `1..100`. Crop coordinates and dimensions are normalized finite numbers from `0` to `1`; dimensions are positive and the rectangle must stay inside the image bounds.

## Status codes

`400` validation failure, `401` missing/invalid/expired session, `404` unknown or not-owned resource, `409` duplicate owner-scoped name, `413` oversized standalone-API body, `415` non-JSON body, `429` rate limited, `500` unexpected failure, and `503` failed readiness. A `405` response includes `Allow`; a `429` response includes `Retry-After`. API clients should display the safe message and include the `requestId` in support reports.
