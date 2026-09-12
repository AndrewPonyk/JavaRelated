# Inventory API Guide

The backend publishes its machine-readable OpenAPI contract at /v3/api-docs. In the local profile, Swagger UI is available at /swagger-ui.html. Production disables Swagger UI by default while retaining the versioned JSON contract.

## Authentication and common conventions

Production requests use an OIDC bearer access token. The backend validates issuer, signature, expiry, and the inventory-api audience.

| Scope | Capabilities |
|---|---|
| inventory:read | Read warehouses, inventory, movements, reservations, forecasts, and alerts |
| inventory:write | Create/update inventory, stock commands, reservations, and alert acknowledgement |
| inventory:admin | Warehouse administration, item deactivation, forecast refresh, audit, and reconciliation |

All retryable writes require an Idempotency-Key containing 8-128 letters, digits, dots, underscores, colons, or hyphens. Reusing a key with a different operation or body returns 409. List APIs return a page object with content, page, size, totalElements, and totalPages.

Errors use application/problem+json:

    {
      "type": "https://inventory.example.com/problems/validation_failed",
      "title": "Invalid request",
      "status": 400,
      "detail": "One or more request fields are invalid.",
      "instance": "/api/v1/inventory",
      "code": "validation_failed",
      "errors": { "sku": "must not be blank" }
    }

## REST endpoint reference

| Method and path | Scope | Request / example |
|---|---|---|
| GET /api/v1/warehouses?page=0&size=50 | read | Paginated warehouses |
| POST /api/v1/warehouses | admin | {"code":"NORTH","name":"North warehouse"} |
| GET /api/v1/warehouses/{id} | read | Warehouse UUID in path |
| PUT /api/v1/warehouses/{id} | admin | {"name":"North distribution","active":true,"version":0} |
| DELETE /api/v1/warehouses/{id} | admin | Deactivates an empty warehouse |
| GET /api/v1/inventory | read | Optional warehouseId, query, active, page, size, sort, direction |
| POST /api/v1/inventory | write | Item body shown below; requires Idempotency-Key |
| GET /api/v1/inventory/{id} | read | Returns ETag with the entity version |
| PUT /api/v1/inventory/{id} | write | Editable item body with version; requires Idempotency-Key |
| DELETE /api/v1/inventory/{id} | admin | {"reason":"Discontinued","version":2} |
| GET /api/v1/inventory/barcode/{barcode} | read | Optional warehouseId disambiguates duplicate cross-warehouse barcodes |
| POST /api/v1/inventory/{id}/adjustments | write | {"delta":-2,"reason":"Cycle count","reference":"COUNT-7"} |
| POST /api/v1/inventory/{id}/receipts | write | {"quantity":25,"reason":"Purchase order","reference":"PO-10"} |
| POST /api/v1/inventory/{id}/shipments | write | {"quantity":4,"reason":"Customer shipment","reference":"SHIP-8"} |
| POST /api/v1/inventory/bulk-adjustments | write | {"entries":[{"itemId":"UUID","delta":1,"reason":"Cycle count"}]} |
| POST /api/v1/inventory/{id}/reservations | write | {"quantity":3,"externalReference":"ORDER-1","reason":"Customer order"} |
| GET /api/v1/inventory/{id}/reservations?page=0&size=20 | read | Paginated reservation history |
| POST /api/v1/reservations/{id}/release | write | Empty body; requires Idempotency-Key |
| POST /api/v1/reservations/{id}/fulfill | write | Empty body; requires Idempotency-Key |
| POST /api/v1/transfers | write | {"sourceItemId":"UUID","destinationWarehouseId":"UUID","quantity":2,"reason":"Rebalance","reference":"MOVE-1"} |
| GET /api/v1/inventory/{id}/movements?page=0&size=50 | read | Paginated immutable stock ledger |
| GET /api/v1/inventory/{id}/forecast | read | Latest persisted forecast |
| POST /api/v1/inventory/{id}/forecast/refresh?horizonDays=14 | admin | Horizon range is 1-90 |
| GET /api/v1/alerts?status=OPEN&page=0&size=20 | read | Paginated low-stock alerts |
| POST /api/v1/alerts/{id}/acknowledge | write | Acknowledges one alert |
| GET /api/v1/audit?entityType=InventoryItem&entityId=UUID | admin | Paginated entity audit history |
| POST /api/v1/admin/reconciliation?maxResults=1000 | admin | Returns scanned count, mismatch count, truncation flag, and bounded mismatches |

Create an item:

    $body = @{
      sku = 'SKU-001'
      barcode = '4006381333931'
      symbology = 'EAN_13'
      aliases = @()
      name = 'Demo item'
      quantity = 10
      reorderPoint = 3
      warehouseId = '00000000-0000-0000-0000-000000000001'
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/inventory -Headers @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() } -ContentType application/json -Body $body

An update body keeps SKU and warehouse immutable:

    {
      "name": "Demo item - revised",
      "barcode": "4006381333931",
      "symbology": "EAN_13",
      "aliases": [],
      "reorderPoint": 5,
      "active": true,
      "version": 1
    }

## GraphQL

POST /graphql accepts bounded read queries. Page must be non-negative and size must be positive; inventory and warehouse sizes cap at 100 and movement sizes cap at 200.

    {
      "query": "{ inventoryItems(page: 0, size: 20) { id sku name quantity availableQuantity } }"
    }

Available queries are inventoryItems, inventoryItem, warehouses, stockMovements, and latestForecast. Mutations intentionally remain REST commands so HTTP idempotency semantics stay explicit.

## Forecast service

The service is cluster-internal in production.

| Method and path | Purpose |
|---|---|
| GET /health/live | Process liveness without checking Redis |
| GET /health/ready | Readiness; returns 503 when Redis is unavailable |
| GET /health | Compatibility alias for readiness |
| GET /v1/models/active | Active model metadata |
| POST /v1/models/train | Train and persist a model; production requires X-Model-Admin-Token |
| POST /v1/forecasts | Generate a versioned forecast from bounded demand history |

Forecast example:

    {
      "sku": "SKU-001",
      "demand_history": [1, 2, 4, 3, 5],
      "horizon_days": 7,
      "feature_version": "daily-demand-v1"
    }

## Status codes

| Status | Meaning |
|---|---|
| 200 / 201 / 204 | Success, creation, or successful no-content operation |
| 400 | Syntax, header, pagination, or field validation error |
| 401 | Missing, invalid, expired, wrong-issuer, or wrong-audience token |
| 403 | Valid token without the required scope |
| 404 | Resource or route not found |
| 409 | Idempotency, uniqueness, version, or state conflict |
| 415 | Request body is not application/json |
| 422 | Valid syntax rejected by an inventory business rule |
| 500 | Unexpected server failure with no stack trace or sensitive detail in the response |
| 503 | Required dependency or model administration configuration is unavailable |
