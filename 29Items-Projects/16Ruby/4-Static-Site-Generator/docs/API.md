# API Documentation

The Sinatra API is intended for local preview, CI validation, and controlled authoring workflows.

## Health

```http
GET /health
```

Response:

```json
{ "status": "ok" }
```

## List Pages

```http
GET /api/pages?limit=50&offset=0
```

Returns parsed Markdown pages with pagination metadata.

Response:

```json
{
  "total": 3,
  "limit": 50,
  "offset": 0,
  "pages": []
}
```

## Get Page

```http
GET /api/pages/:version/:slug
```

Returns metadata and rendered HTML for a single page.

## Create Page

```http
POST /api/pages
Content-Type: application/json
Authorization: Bearer <API_AUTH_TOKEN>
```

Request:

```json
{
  "title": "Install",
  "slug": "install",
  "version": "v2",
  "description": "Installation guide",
  "tags": ["setup"],
  "body": "# Install\n\nRun the installer."
}
```

Response status: `201 Created`.

The authorization header is required when `APP_ENV=production`.

## Update Page

```http
PUT /api/pages/:version/:slug
Content-Type: application/json
Authorization: Bearer <API_AUTH_TOKEN>
```

Any page field can be updated. Changing `version` or `slug` moves the Markdown file.

## Delete Page

```http
DELETE /api/pages/:version/:slug
Authorization: Bearer <API_AUTH_TOKEN>
```

Deletes the Markdown file and removes metadata rows.

## Search

```http
GET /api/search?q=install&limit=10
```

Returns ranked search results based on title, summary, tags, keywords, and body text.
Search queries are limited to 80 characters and result limits are clamped to 1-50.

## Versions

```http
GET /api/versions
```

Returns discovered documentation versions and the latest version.

## Build

```http
POST /api/build
Authorization: Bearer <API_AUTH_TOKEN>
```

Runs the static build pipeline and returns the number of generated pages.
The authorization header is required when `APP_ENV=production`.

## Error Format

```json
{
  "error": "validation_failed",
  "message": "Validation failed",
  "details": ["title is required"]
}
```

Common status codes:

- `400`: malformed JSON, wrong content type, oversized body, or invalid query.
- `401`: missing or invalid production write token.
- `404`: requested page does not exist.
- `409`: duplicate page version and slug.
- `422`: validation failed.
- `500`: unexpected server error.
