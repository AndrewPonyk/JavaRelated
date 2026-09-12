# Policy API Reference

Base URL (local): `http://localhost:5101` · Interactive docs: `/swagger` (Development only).

## Authentication

Production validates OIDC JWTs (`Authorization: Bearer …`) issued by Portal.Identity.
Locally (no `Identity:Authority` configured) the API uses the header-driven **Dev scheme**:

| Header | Meaning | Default |
|---|---|---|
| `X-Dev-Role` | `Broker`, `Underwriter`, or `Customer` | `Broker` |
| `X-Dev-User` | Actor name recorded in the audit trail | `dev-broker` |
| `X-Dev-CustomerId` | Customer GUID — required for `Customer` role scoping | — |

Roles: **Broker/Underwriter** manage customers, quotes, policies and adjudicate claims.
**Customer** sees only their own policies and may file claims against them.

## Conventions

- List endpoints take `?page=` (1-based) and `?pageSize=` (max 200, default 50); out-of-range
  values are clamped. The total row count is returned in the `X-Total-Count` response header.
- Errors are RFC 7807 `application/problem+json`: `400` validation, `401` bad/missing token
  (JWT mode), `403` insufficient role, `404` missing or foreign resource, `422` business-rule
  violation, `429` rate limited, `503` rating engine unreachable.
- All requests are rate limited per caller (default 100 req/s, `RateLimiting:PermitPerSecond`).

## Customers (Broker)

```bash
# Create
curl -s -X POST http://localhost:5101/api/v1/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","dateOfBirth":"1990-12-10"}'
# → 201 {"id":"…","firstName":"Ada",…} · 422 if email exists or customer is under 18

# List / get
curl -s 'http://localhost:5101/api/v1/customers?page=1&pageSize=50'
curl -s http://localhost:5101/api/v1/customers/{id}
```

## Quotes (Broker)

```bash
# Rate & issue (calls Rating.Grpc; quote valid 30 days)
curl -s -X POST http://localhost:5101/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"<guid>","brokerId":"BRK-001","productCode":"AUTO-STD","stateCode":"CA",
       "riskFactors":{"driverAge":"22","priorClaims":"1","vehicleValue":"45000"}}'
# → 201 {"premium":2453.75,"rateTableVersion":"2026.06","status":"Issued",…}
# → 422 unknown product · 503 rating engine down

curl -s 'http://localhost:5101/api/v1/quotes?brokerId=BRK-001'
curl -s -X POST http://localhost:5101/api/v1/quotes/{id}/decline
```

Products: `AUTO-STD`, `AUTO-PREM`, `HOME-STD`, `HOME-PREM`, `LIFE-TERM`.
Risk factors: `driverAge`, `priorClaims`, `vehicleValue`, `propertyValue`, `smoker` (unknown keys ignored).

## Policies

```bash
# Bind an issued quote (Broker) — emits PolicyBoundEvent via the outbox
curl -s -X POST http://localhost:5101/api/v1/policies \
  -H 'Content-Type: application/json' \
  -d '{"quoteId":"<guid>","brokerId":"BRK-001"}'
# → 201 · 404 unknown quote · 422 quote not Issued / expired

# List (customers are auto-scoped to their own, whatever filter they pass)
curl -s 'http://localhost:5101/api/v1/policies?customerId=<guid>&page=1&pageSize=50'
curl -s 'http://localhost:5101/api/v1/policies' \
  -H 'X-Dev-Role: Customer' -H 'X-Dev-CustomerId: <guid>'

# Detail (includes customer name + claims)
curl -s http://localhost:5101/api/v1/policies/{id}

# Cancel (Broker) — reason required; emits PolicyCancelledEvent
curl -s -X DELETE http://localhost:5101/api/v1/policies/{id} \
  -H 'Content-Type: application/json' -d '{"reason":"non-payment"}'
# → 200 · 422 already cancelled
```

## Claims

```bash
# File (Broker, or the policy's own Customer) — emits ClaimFiledEvent
curl -s -X POST http://localhost:5101/api/v1/policies/{policyId}/claims \
  -H 'Content-Type: application/json' \
  -d '{"description":"Rear-end collision","claimedAmount":3200}'
# → 201 status "Filed" · 404 foreign policy · 422 policy not Active

curl -s http://localhost:5101/api/v1/policies/{policyId}/claims

# Adjudicate (Broker/Underwriter only): Filed → UnderReview → Approved|Rejected → Paid
curl -s -X POST http://localhost:5101/api/v1/claims/{claimId}/status \
  -H 'Content-Type: application/json' -d '{"action":"review"}'
curl -s -X POST http://localhost:5101/api/v1/claims/{claimId}/status \
  -H 'Content-Type: application/json' -d '{"action":"approve","approvedAmount":2900}'
# actions: review | approve (amount required, ≤ claimed) | reject | pay
# → 422 illegal transition or amount > claimed · 403 as Customer
```

## Operations

```bash
curl -s http://localhost:5101/healthz   # liveness/readiness (includes DB check)
```

Events published to Kafka topic `policy-events` (key = PolicyId): `PolicyBoundEvent`,
`PolicyCancelledEvent`, `ClaimFiledEvent`, `ClaimStatusChangedEvent`, `PremiumRecalculatedEvent` —
contracts in `src/Shared/Portal.Shared.Contracts/Events/PolicyEvents.cs`.
