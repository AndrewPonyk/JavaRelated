# API Contract

## Conventions

The API uses JSON and UUID identifiers. Unknown JSON fields are rejected. Successful delete/logout/password-change responses are `204 No Content`. Validation failures are `400`, missing authentication is `401`, insufficient role or membership is `403`, missing resources are `404`, unsupported methods are `405`, uniqueness/state conflicts are `409`, oversized bodies are `413`, non-JSON bodies are `415`, rate limits are `429`, and dependency outages are `503` where recoverable.

Every HTTP failure uses:

```json
{
  "error": {
    "code": "validation_error",
    "message": "room name cannot be empty"
  }
}
```

Responses include `x-request-id`, a restrictive content security policy, HSTS, a restrictive permissions policy, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: no-referrer`. Compressible responses support Brotli and gzip; API responses use `Cache-Control: no-store`.

Every state-changing request and WebSocket upgrade must include an exact `Origin` from `ALLOWED_ORIGINS`. This also applies to non-browser clients because authentication is cookie-based.

## Authentication and users

`POST /api/auth/register` and `POST /api/auth/login` accept:

```json
{ "username": "Ada", "password": "correct horse 42" }
```

Usernames contain 3–40 letters, numbers, spaces, `_`, `-`, or `.`. Passwords contain 12–128 UTF-8 bytes with at least one letter and number. Registration returns `201`; login returns `200`. Both return a session identity and set a revocable `HttpOnly; SameSite=Strict` cookie:

```json
{
  "session_id": "2fbba022-6074-4839-9965-44013776f433",
  "user": {
    "id": "0cecb5e4-6fae-4ec8-ac70-a57ef33113ee",
    "username": "Ada",
    "created_at": "2026-07-25T12:00:00Z",
    "updated_at": "2026-07-25T12:00:00Z"
  },
  "expires_at": "2026-07-26T12:00:00Z"
}
```

- `GET /api/auth/me` or `GET /api/users/me`: current identity.
- `POST /api/auth/logout`: revoke the current token and expire its cookie.
- `PUT /api/users/me`: `{ "username": "Ada Updated" }`.
- `POST /api/users/me/password`: `{ "current_password": "...", "new_password": "..." }`; all sessions are revoked.
- `DELETE /api/users/me`: `{ "password": "..." }`; sessions and memberships are removed, rooms owned by the account are deleted, the account row is anonymized/deactivated for referential integrity, and messages sent in rooms owned by others retain their stored display name.

## Rooms and memberships

`GET /api/rooms?offset=0&limit=100` is optionally authenticated and bounded by `MAX_LIST_PAGE_SIZE`. Anonymous clients see public rooms; authenticated clients also see private rooms where they are members. A room view includes `member_count`, live `online_count`, and `current_user_role`.

`POST /api/rooms` accepts:

```json
{
  "name": "Engineering",
  "description": "Build together",
  "is_private": false
}
```

The creator becomes owner. Room names are unique case-insensitively. Owners and moderators can `PUT /api/rooms/{room_id}`; only owners can delete. Deleting a room cascades durable messages/memberships, broadcasts a terminal room event, and closes active room subscriptions.

Authenticated users join public rooms with `POST /api/rooms/{room_id}/join`. `GET /api/rooms/{room_id}/members?offset=0&limit=100` is likewise bounded. Private rooms require an owner/moderator invitation:

```json
{ "username": "Grace", "role": "member" }
```

at `POST /api/rooms/{room_id}/members`. Roles are `member`, `moderator`, and `owner`. Owners can change members between `member` and `moderator` with `PUT /api/rooms/{room_id}/members/{user_id}`; this endpoint cannot transfer ownership. Members can leave, moderators can remove members, and owners can remove moderators with `DELETE` on the same route. Owner removal/demotion is rejected in both the service and database so the room never silently loses control.

## Messages and history

All message routes require room membership. `GET /api/rooms/{room_id}/messages?limit=50&before=...` returns messages in chronological display order:

```json
{
  "messages": [],
  "next_cursor": "MjAyNi0wNy0yNVQxMjo..."
}
```

Pass `next_cursor` as `before` to fetch the next older page. Limits are clamped to the configured maximum; the opaque cursor combines the stable `(created_at, id)` ordering key.

`POST /api/rooms/{room_id}/messages` accepts a client UUID for durable idempotency:

```json
{
  "client_message_id": "287175d0-6d68-44cf-83a4-e42db747c763",
  "content": "Hello"
}
```

The first write returns `201`; retrying the same UUID for the same room/user returns the original message with `200` and does not rebroadcast it. Content is trimmed, nonblank, null-free, and limited by UTF-8 bytes.

Senders and moderators can `PUT /api/rooms/{room_id}/messages/{message_id}` with `{ "content": "Edited" }`. They can `DELETE` the message; deletion erases content while retaining ID, sender, timestamps, and a `deleted_at` tombstone. `GET /api/rooms/{room_id}/audit` exposes immutable moderation/room/member events to owners and moderators.

## WebSocket protocol

`GET /ws/{room_id}` requires a valid session cookie, allowed `Origin`, and room membership. Commands and events are tagged JSON. Binary frames and unknown fields are rejected.

Client commands:

```json
{ "type": "send_message", "client_message_id": "...", "content": "Hello" }
{ "type": "edit_message", "message_id": "...", "content": "Edited" }
{ "type": "delete_message", "message_id": "..." }
{ "type": "typing", "is_typing": true }
{ "type": "ping", "nonce": "..." }
```

Server event types are `message_created`, `message_updated`, `message_deleted`, `user_joined`, `user_left`, `online_count`, `typing`, `error`, and `pong`. Error events include stable `code`, safe `message`, and `retryable`.

The server persists before broadcasting. A client falling behind the bounded room channel receives `resync_required` and should reload history. Server ping frames plus activity deadlines remove stale connections. Session validity is rechecked on each heartbeat; expired or revoked sessions receive policy close code `1008`. During shutdown, sockets receive close code `1012`; the browser reconnects with bounded exponential backoff and reloads durable history.

## Health

- `GET /health/live` always returns `{ "status": "ok" }` while the process can serve requests.
- `GET /health/ready` runs `SELECT 1`; it returns `200 { "status": "ready" }` or `503 { "status": "not_ready" }`.
