from math import asin, cos, radians, sin, sqrt


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius_km = 6371.0088
    d_lat = radians(lat2 - lat1)
    d_lon = radians(lon2 - lon1)
    a = (
        sin(d_lat / 2) ** 2
        + cos(radians(lat1)) * cos(radians(lat2)) * sin(d_lon / 2) ** 2
    )
    return 2 * radius_km * asin(sqrt(a))


def point_in_polygon(latitude: float, longitude: float, polygon: dict) -> bool:
    coordinates = polygon.get("coordinates", [])
    if polygon.get("type") != "Polygon" or not coordinates:
        return False

    ring = coordinates[0]
    inside = False
    j = len(ring) - 1
    x = longitude
    y = latitude

    for i, point in enumerate(ring):
        xi, yi = point[0], point[1]
        xj, yj = ring[j][0], ring[j][1]
        intersects = (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-12) + xi
        if intersects:
            inside = not inside
        j = i

    return inside


def validate_polygon_geojson(value: dict) -> dict:
    if value.get("type") != "Polygon":
        raise ValueError("Boundary must be a GeoJSON Polygon")
    coordinates = value.get("coordinates")
    if not isinstance(coordinates, list) or not coordinates or len(coordinates[0]) < 4:
        raise ValueError("Polygon must include a closed linear ring with at least four positions")
    first = coordinates[0][0]
    last = coordinates[0][-1]
    if first != last:
        raise ValueError("Polygon linear ring must be closed")
    return value
