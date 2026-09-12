# API Reference

Base URL: `http://localhost:8080`

All successful collection and object responses use `{ "data": ... }`. Errors use:

```json
{
  "error": {
    "code": "validation_failed",
    "message": "Request validation failed.",
    "details": {}
  }
}
```

Set `REQUIRE_FIREBASE_AUTH=true` to require `Authorization: Bearer <Firebase ID token>`
on POI, route, beacon, WiFi, positioning, and heatmap routes.

## Health

- `GET /health`

## Venues

- `GET /api/venues?query=&venueType=&limit=`
- `GET /api/venues/:id`
- `POST /api/venues`
- `PATCH /api/venues/:id`
- `DELETE /api/venues/:id`

Create body:

```json
{
  "name": "Central Mall",
  "venueType": "mall",
  "timezone": "Europe/Kiev",
  "boundary": {
    "type": "Polygon",
    "coordinates": [
      [
        [30.52, 50.45],
        [30.53, 50.45],
        [30.53, 50.46],
        [30.52, 50.45]
      ]
    ]
  }
}
```

## Floors

- `GET /api/floors?venueId=&limit=`
- `GET /api/floors/:id`
- `POST /api/floors`
- `PATCH /api/floors/:id`
- `DELETE /api/floors/:id`

## POIs

- `GET /api/pois?venueId=&floorId=&category=&query=&latitude=&longitude=&limit=`
- `GET /api/pois/:id`
- `POST /api/pois`
- `PATCH /api/pois/:id`
- `DELETE /api/pois/:id`

Create body:

```json
{
  "venueId": "11111111-1111-1111-1111-111111111111",
  "floorId": "22222222-2222-2222-2222-222222222222",
  "name": "Cafe",
  "category": "food",
  "description": "Coffee and snacks",
  "latitude": 50.45,
  "longitude": 30.52
}
```

## Routes

- `GET /api/routes/nodes?venueId=&floorId=&nodeType=&limit=`
- `GET /api/routes/nodes/:id`
- `POST /api/routes/nodes`
- `PATCH /api/routes/nodes/:id`
- `DELETE /api/routes/nodes/:id`
- `GET /api/routes/edges?venueId=&fromNodeId=&toNodeId=&accessibleOnly=&limit=`
- `GET /api/routes/edges/:id`
- `POST /api/routes/edges`
- `PATCH /api/routes/edges/:id`
- `DELETE /api/routes/edges/:id`
- `GET /api/routes/directions?venueId=&fromNodeId=&toNodeId=&accessibleOnly=`

Route edge geometry is GeoJSON `LineString`.

## Beacon Anchors

- `GET /api/beacon-anchors?venueId=&floorId=&provider=&limit=`
- `GET /api/beacon-anchors/:id`
- `POST /api/beacon-anchors`
- `PATCH /api/beacon-anchors/:id`
- `DELETE /api/beacon-anchors/:id`

## WiFi Fingerprints

- `GET /api/wifi-fingerprints?venueId=&floorId=&limit=`
- `GET /api/wifi-fingerprints/:id`
- `POST /api/wifi-fingerprints`
- `PATCH /api/wifi-fingerprints/:id`
- `DELETE /api/wifi-fingerprints/:id`

## Positioning Events

- `GET /api/positioning-events?venueId=&floorId=&userHash=&limit=`
- `GET /api/positioning-events/:id`
- `POST /api/positioning-events`
- `POST /api/positioning-events/ingest`
- `PATCH /api/positioning-events/:id`
- `DELETE /api/positioning-events/:id`

Ingest body:

```json
{
  "venueId": "11111111-1111-1111-1111-111111111111",
  "floorId": "22222222-2222-2222-2222-222222222222",
  "userHash": "anonymous-user",
  "wifiReadings": [{ "bssid": "ap-1", "rssi": -55 }],
  "beaconReadings": [{ "provider": "estimote", "externalId": "beacon-a", "rssi": -70 }]
}
```

## Heatmap Cells

- `GET /api/heatmap-cells?venueId=&floorId=&latestOnly=&limit=`
- `GET /api/heatmap-cells/:id`
- `POST /api/heatmap-cells`
- `PATCH /api/heatmap-cells/:id`
- `DELETE /api/heatmap-cells/:id`

Heatmap cell geometry is GeoJSON `Polygon`.
