# Hotel Booking Website API

All responses use one of these envelopes:

```json
{ "data": {} }
```

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Human-readable message" } }
```

List endpoints accept `limit` and `offset` query parameters. `limit` defaults to `50` and is capped at `100`.

## Public Endpoints

### GET `/api/hotels`

Query parameters: `city`, `active`, `limit`, `offset`.

### GET `/api/rooms`

Query parameters: `hotelId`, `status`, `limit`, `offset`.

### GET `/api/availability`

Required query parameters: `checkIn`, `checkOut`, `guests`.

Optional query parameters: `roomType`, `city`, `hotelId`, `limit`, `offset`.

Example:

```http
GET /api/availability?checkIn=2026-08-01&checkOut=2026-08-03&guests=2&roomType=standard
```

### POST `/api/bookings`

Creates a confirmed booking after rechecking room availability in a transaction.

```json
{
  "checkIn": "2026-08-01",
  "checkOut": "2026-08-03",
  "guests": 2,
  "roomType": "standard",
  "city": "New York",
  "guestName": "Alex Morgan",
  "guestEmail": "alex@example.com",
  "guestPhone": "+1-555-0101",
  "specialRequests": "Late arrival"
}
```

### GET `/api/bookings?confirmationCode=HB-...`

Returns one booking by confirmation code.

## Staff Endpoints

Staff endpoints require:

```http
x-staff-token: <STAFF_API_TOKEN>
x-staff-role: manager
```

Use `x-staff-role: receptionist` for non-manager booking/room updates where allowed.

### Hotels

- `POST /api/hotels`
- `PATCH /api/hotels?id=<hotelId>`
- `DELETE /api/hotels?id=<hotelId>`

### Rooms

- `POST /api/rooms`
- `PATCH /api/rooms?id=<roomId>`
- `DELETE /api/rooms?id=<roomId>`

### Guests

- `GET /api/guests`
- `POST /api/guests`
- `PATCH /api/guests?id=<guestId>`
- `DELETE /api/guests?id=<guestId>`

### Bookings

- `GET /api/bookings`
- `GET /api/bookings?id=<bookingId>`
- `PATCH /api/bookings?id=<bookingId>`
- `DELETE /api/bookings?id=<bookingId>`

### Staff Users

- `GET /api/staff-users`
- `POST /api/staff-users`
- `PATCH /api/staff-users?id=<staffUserId>`
- `DELETE /api/staff-users?id=<staffUserId>`
