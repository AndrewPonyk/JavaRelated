# API Documentation

Base URL: `http://localhost:8080`

Protected endpoints require `X-API-Key` when `ADMIN_API_KEYS` is configured. If `ADMIN_API_KEYS` is empty, protected endpoints are open for local development.

Example protected request:

```bash
curl http://localhost:8080/api/v1/urls \
  -H "X-API-Key: your-admin-key"
```

## Health

`GET /api/v1/health`

Response:

```json
{"status":"ok"}
```

## Create URL

`POST /api/v1/urls`

Request:

```json
{
  "originalUrl": "https://example.com/article",
  "customCode": "article",
  "title": "Example article",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

Response: `201 Created`

```json
{
  "id": "uuid",
  "shortCode": "article",
  "shortUrl": "http://localhost:8080/article",
  "originalUrl": "https://example.com/article",
  "title": "Example article",
  "createdAt": "2026-04-30T12:00:00Z",
  "updatedAt": "2026-04-30T12:00:00Z",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

## List URLs

`GET /api/v1/urls?limit=20`

Protected.

## Get URL

`GET /api/v1/urls/{code}`

Returns URL metadata.

## Update URL

`PATCH /api/v1/urls/{code}`

Protected.

Request:

```json
{
  "originalUrl": "https://example.com/new-target",
  "title": "Updated title",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

## Delete URL

`DELETE /api/v1/urls/{code}`

Protected. Returns `204 No Content`.

## Redirect

`GET /{code}`

Returns `302 Found` with a `Location` header. The redirect records analytics in Redis and PostgreSQL.

## Analytics

`GET /api/v1/urls/{code}/analytics`

Protected.

Response:

```json
{
  "shortCode": "article",
  "totalClicks": 10,
  "byReferrer": {"direct": 4},
  "byCountry": {"US": 6},
  "byUserAgent": {"Mozilla/5.0": 10},
  "byBucket": [{"timestamp": "2026-04-30T12:00:00Z", "clicks": 10}]
}
```

## QR Code

`GET /api/v1/urls/{code}/qr`

Returns `image/png`.

## Error Shape

```json
{
  "error": {
    "code": "validation_failed",
    "message": "validation failed: invalid url"
  }
}
```
