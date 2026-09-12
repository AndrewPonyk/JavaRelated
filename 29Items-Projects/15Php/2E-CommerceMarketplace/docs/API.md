# E-Commerce Marketplace — HTTP API

All endpoints are served under `/api` (except health probes). Requests and
responses are JSON. Authenticated endpoints expect `Authorization: Bearer <JWT>`
(obtain a token via `POST /api/auth/login`).

Errors use a consistent envelope (see `ApiExceptionListener`):

```json
{ "error": { "type": "validation_error", "message": "…", "correlationId": "…" } }
```

| Status | When |
|--------|------|
| 400/422 | invalid input (validation) |
| 401 | missing/invalid token |
| 403 | authenticated but not allowed (e.g. not the owner) |
| 404 | resource not found |
| 409 | domain conflict (e.g. payment blocked, email already used) |
| 402 | payment declined by the PSP |
| 429 | rate limited (search; failed logins) |

---

## Health

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/health/live` | public | Liveness probe. |
| GET | `/health/ready` | public | Readiness probe (checks DB). |

## Identity / Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | public | Create a `customer` or `seller` account. |
| POST | `/api/auth/login` | public | Exchange credentials for a JWT. |
| GET | `/api/auth/me` | customer+ | The authenticated user's identity. |

`POST /api/auth/register`

```json
{ "email": "a@b.com", "password": "at-least-8-chars", "accountType": "seller" }
→ 201 { "id": "…", "email": "a@b.com", "roles": ["ROLE_SELLER","ROLE_CUSTOMER"] }
```

`POST /api/auth/login`

```json
{ "email": "a@b.com", "password": "…" } → 200 { "token": "<jwt>" }
```

## Catalog (products)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/products` | public | List active products (paginated: `page`, `perPage`). |
| GET | `/api/products/{id}` | public | A single product. |
| GET | `/api/products/mine` | seller | The seller's own products. |
| POST | `/api/products` | seller | Create a product. |
| PATCH | `/api/products/{id}` | seller (owner) | Partial update (name, description, price, stock, active). |

`POST /api/products`

```json
{ "name": "Mouse", "description": "Wireless", "priceMinor": 2599, "currency": "USD", "stock": 10 }
→ 201 { "id": "…" }   (Location: /api/products/{id})
```

## Search

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/search?q=&page=&perPage=` | public (rate-limited) | Full-text product search (Elasticsearch read model). |

```json
→ 200 { "total": 1, "items": [ { "id":"…","name":"Mouse","priceMinor":2599,"currency":"USD","sellerId":"…" } ] }
```

## Ordering (event-sourced)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/orders` | customer | Place an order (returns `202`, status `PENDING`). |
| GET | `/api/orders/{id}` | customer (owner) / admin | Order detail, reconstructed from its event stream. |

`POST /api/orders`

```json
{ "currency": "USD", "lines": [ { "productId":"…","sellerId":"…","quantity":2,"unitPriceMinor":2599 } ] }
→ 202 { "id": "…", "status": "PENDING" }
```

## Payment

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/payments/capture` | customer | Capture payment for an order (idempotent). |

`POST /api/payments/capture`

```json
{ "orderId":"…", "amountMinor":5198, "currency":"USD", "paymentMethodToken":"tok_visa", "idempotencyKey":"optional" }
→ 200 { "transactionId":"…", "status":"CAPTURED", "pspReference":"…", "idempotencyKey":"…" }
→ 402 when the PSP declines · 409 when a fraud hold is in place
```

## Vendor (seller dashboard)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/seller/dashboard/commission` | seller | Commission summary (gross, commission owed, order count). |
| GET | `/api/seller/dashboard/ledger` | seller | Recent commission ledger entries (payout history). |

## FraudDetection (admin review queue)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/admin/fraud/reviews` | admin | Pending flagged orders. |
| POST | `/api/admin/fraud/reviews/{orderId}/resolve` | admin | Clear (`{"cleared": true}`) or confirm fraud. |

---

## Async flows (no direct HTTP)

These happen on the event bus (RabbitMQ) after `OrderPlaced` / `PaymentCaptured`:

- **Search** indexes `ProductCreated` / `ProductPriceChanged`.
- **Vendor** accrues commission per seller on `OrderPlaced`.
- **FraudDetection** scores risk on `OrderPlaced`; a BLOCK emits `OrderFlagged`.
- **Payment** holds capture on `OrderFlagged` (BLOCK); lifts it on `FraudReviewResolved` (cleared).
- **Ordering** transitions an order to `PAID` on `PaymentCaptured`.
