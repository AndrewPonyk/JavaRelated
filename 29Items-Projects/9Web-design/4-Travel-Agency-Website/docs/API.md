# API Documentation

All responses use JSON. Successful list responses use `{ "data": [...] }`; single-resource responses use `{ "data": {...} }`.

List endpoints support pagination with:

- `limit`: integer from 1 to 100, default `20`
- `offset`: integer greater than or equal to 0, default `0`

List responses include pagination metadata:

```json
{
  "data": [],
  "meta": {
    "total": 6,
    "count": 2,
    "limit": 2,
    "offset": 0
  }
}
```

## Health

- `GET /health`

Example response:

```json
{
  "status": "ok",
  "database": "ok",
  "uptime": 12.34
}
```

## Tours

- `GET /api/tours`
- `GET /api/tours/:id`
- `POST /api/tours`
- `PATCH /api/tours/:id`
- `DELETE /api/tours/:id`

Supported list query parameters:

- `region`: `europe`, `asia`, `americas`, `africa`
- `difficulty`: `easy`, `moderate`, `active`
- `featured`: `true` or `false`
- `maxPrice`: whole-dollar maximum
- `maxDuration`: maximum days
- `sort`: `price-asc`, `price-desc`, `duration-asc`, `duration-desc`, or `featured`

Tour payload fields:

```json
{
  "slug": "amalfi-rail-coast",
  "title": "Amalfi Rail and Coast",
  "region": "europe",
  "regionLabel": "Europe",
  "durationDays": 8,
  "price": 3400,
  "difficulty": "easy",
  "featured": true,
  "summary": "Rome, Naples, and Amalfi with private transfers and coastal day trips.",
  "image": "https://example.com/image.jpg"
}
```

## Destinations

- `GET /api/destinations`
- `GET /api/destinations/:id`
- `POST /api/destinations`
- `PATCH /api/destinations/:id`
- `DELETE /api/destinations/:id`

Destination payload fields:

```json
{
  "slug": "kyoto",
  "name": "Kyoto",
  "region": "Asia",
  "latitude": 35.0116,
  "longitude": 135.7681,
  "description": "Cultural routes, gardens, temples, and traditional inns.",
  "image": "https://example.com/image.jpg"
}
```

## Inquiries

- `GET /api/inquiries`
- `GET /api/inquiries/:id`
- `POST /api/inquiries`
- `PATCH /api/inquiries/:id`
- `DELETE /api/inquiries/:id`

Supported list query parameters:

- `status`: `new`, `contacted`, `booked`, or `closed`

Inquiry creation payload:

```json
{
  "tourId": 1,
  "name": "Taylor Client",
  "email": "taylor@example.com",
  "destination": "Kyoto",
  "budget": "3000-6000",
  "message": "We want a private culture-focused itinerary next spring.",
  "consent": true
}
```

Inquiry update payloads may include contact fields and `status`.
