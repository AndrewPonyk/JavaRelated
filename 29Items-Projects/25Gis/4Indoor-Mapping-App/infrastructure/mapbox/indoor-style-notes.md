# Mapbox Indoor Style Notes

Expected source/layer conventions:

- `indoor-floor-polygons`: Fill layer for floor geometry.
- `indoor-pois`: Symbol layer for POI labels and icons.
- `indoor-route-line`: Line layer for active directions.
- `indoor-heatmap`: Heatmap or raster overlay for crowd density.

Use these layer IDs when exporting a Mapbox style for a venue. The API stores
floor layer references in `floors.mapbox_layer_id`, and the mobile app receives
the Mapbox token through `MAPBOX_ACCESS_TOKEN`.
