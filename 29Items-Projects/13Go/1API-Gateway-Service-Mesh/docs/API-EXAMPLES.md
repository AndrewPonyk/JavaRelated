# API Examples

Set a local key first:

```powershell
$key = "local-dev-key"
```

## Tenants

```powershell
curl.exe -H "X-Admin-API-Key: $key" http://localhost:8080/api/v1/tenants

curl.exe -X POST http://localhost:8080/api/v1/tenants `
  -H "Content-Type: application/json" `
  -H "X-Admin-API-Key: $key" `
  -d "{\"name\":\"acme\",\"status\":\"active\"}"
```

## Routes

```powershell
curl.exe -H "X-Admin-API-Key: $key" "http://localhost:8080/api/v1/routes?limit=50&offset=0"

curl.exe -X POST http://localhost:8080/api/v1/routes `
  -H "Content-Type: application/json" `
  -H "X-Admin-API-Key: $key" `
  -d "{\"tenantId\":\"<tenant-id>\",\"name\":\"orders\",\"host\":\"localhost:8080\",\"pathPrefix\":\"/orders\",\"methods\":[\"GET\"],\"upstreamService\":\"http://httpbin.org\",\"upstreamProtocol\":\"http\",\"rateLimitPerMinute\":120,\"requiredScopes\":[],\"transformHeaders\":{\"X-Gateway\":\"api-gateway\"},\"anomalyProtection\":true}"

curl.exe -H "X-Admin-API-Key: $key" "http://localhost:8080/api/v1/routes/resolve?tenantId=<tenant-id>&host=localhost:8080&path=/orders/123&method=GET"
```

## Anomalies

```powershell
curl.exe -H "X-Admin-API-Key: $key" "http://localhost:8080/api/v1/anomalies?tenantId=<tenant-id>"

curl.exe -X POST http://localhost:8080/api/v1/traffic/score `
  -H "Content-Type: application/json" `
  -H "X-Admin-API-Key: $key" `
  -d "{\"tenantId\":\"<tenant-id>\",\"routeId\":\"<route-id>\",\"requestRate\":2000,\"errorRate\":0.4,\"p95LatencyMs\":3000,\"bytesPerSec\":1000000,\"deployVersion\":\"local\"}"
```

## Health And Metrics

```powershell
curl.exe http://localhost:8080/healthz
curl.exe http://localhost:8080/readyz
curl.exe http://localhost:8080/metrics
```
