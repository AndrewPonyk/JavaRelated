# API Documentation

Base URL: `http://localhost:4100`

If `API_KEY` is configured, all `/api/*` routes require:

```http
x-api-key: your-api-key
```

List endpoints support `limit` from 1 to 100 and `offset` from 0 upward.

## Health

### `GET /health`

Returns API health.

```json
{ "status": "ok" }
```

## Status

### `GET /api/status`

Returns summary data used by `ds-status-card`.

```json
{
  "title": "Design system ready",
  "description": "10 approved, 0 draft, 0 rejected, 0 deprecated tokens.",
  "status": "success",
  "counts": {
    "draft": 0,
    "approved": 10,
    "rejected": 0,
    "deprecated": 0
  }
}
```

## Token Sets

### `GET /api/token-sets`

Lists token sets.

### `POST /api/token-sets`

```json
{
  "name": "core",
  "description": "Canonical tokens",
  "source": "local"
}
```

### `GET /api/token-sets/:id`

Gets one token set.

### `PATCH /api/token-sets/:id`

Updates name, description, or source.

### `DELETE /api/token-sets/:id`

Deletes a token set and cascades tokens.

### `GET /api/token-sets/:id/diff/:compareId`

Compares two token sets by token name and returns `added`, `removed`, `changed`, or `unchanged`.

### `GET /api/token-sets/:id/audit`

Returns audit events for a token set.

## Tokens

### `GET /api/tokens`

Query parameters:

- `tokenSetId`
- `category`
- `status`
- `search`

### `POST /api/tokens`

```json
{
  "tokenSetId": "uuid",
  "name": "color.brand.primary",
  "category": "color",
  "value": "#265CFF",
  "description": "Primary brand action color",
  "figmaNodeId": "optional",
  "createdBy": "designer@example.com",
  "changeNote": "Initial token"
}
```

### `GET /api/tokens/:id`

Gets one token.

### `PATCH /api/tokens/:id`

Updates token fields and records a version when versioned fields change.

### `DELETE /api/tokens/:id`

Deletes a token.

### `POST /api/tokens/:id/approve`

```json
{
  "actor": "reviewer@example.com",
  "changeNote": "Meets WCAG AA contrast requirements"
}
```

### `POST /api/tokens/:id/reject`

Marks a token as rejected.

### `POST /api/tokens/:id/deprecate`

Marks a token as deprecated.

### `GET /api/tokens/:id/versions`

Returns token version history.

### `GET /api/tokens/:id/audit`

Returns token audit events.

### `GET /api/tokens/changelog`

Optional query parameter: `tokenSetId`.
