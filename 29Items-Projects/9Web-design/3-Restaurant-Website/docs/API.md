# Reservation API

Base path: `/api/reservations`

## Response Shape

Success:

```json
{
  "ok": true
}
```

Failure:

```json
{
  "ok": false,
  "error": "Please enter a valid email address.",
  "code": "validation_error",
  "details": [{ "field": "email", "message": "Please enter a valid email address." }]
}
```

## Reservation Model

```json
{
  "id": "uuid",
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "date": "2026-06-01",
  "partySize": 4,
  "notes": "Window table if available",
  "status": "requested",
  "createdAt": "2026-05-18T10:00:00.000Z",
  "updatedAt": "2026-05-18T10:00:00.000Z"
}
```

Valid statuses: `requested`, `confirmed`, `declined`, `cancelled`.

## Endpoints

### `GET /api/reservations`

Optional query parameters:

- `status`
- `date`
- `page` positive integer, defaults to `1`
- `pageSize` positive integer, defaults to `20`, maximum `100`

Example response:

```json
{
  "ok": true,
  "reservations": [],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 0,
    "totalPages": 0
  }
}
```

### `POST /api/reservations`

Creates a reservation request.

Required body fields: `name`, `email`, `date`, `partySize`.

### `GET /api/reservations/:id`

Returns one reservation by id.

### `PUT /api/reservations/:id`

Updates one or more fields: `name`, `email`, `date`, `partySize`, `notes`, `status`.

### `DELETE /api/reservations/:id`

Deletes and returns the deleted reservation.
