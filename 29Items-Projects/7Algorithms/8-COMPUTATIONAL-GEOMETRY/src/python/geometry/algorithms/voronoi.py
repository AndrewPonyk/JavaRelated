from dataclasses import dataclass
from math import atan2

from geometry.algorithms.delaunay import triangle_circumcenter, triangulate
from geometry.model.point import Point2D


@dataclass(frozen=True)
class VoronoiCell:
    site: Point2D
    vertices: tuple[Point2D, ...]


def build_voronoi(sites: list[Point2D]) -> list[VoronoiCell]:
    unique_sites = sorted(set(sites))
    centers_by_site: dict[Point2D, list[Point2D]] = {site: [] for site in unique_sites}
    for triangle in triangulate(unique_sites):
        center = triangle_circumcenter(triangle)
        if center is None:
            continue
        for site in triangle:
            if not _contains_point(centers_by_site[site], center):
                centers_by_site[site].append(center)

    cells: list[VoronoiCell] = []
    for site, centers in centers_by_site.items():
        ordered = tuple(sorted(centers, key=lambda p: atan2(p.y - site.y, p.x - site.x)))
        cells.append(VoronoiCell(site, ordered))
    return cells


def _contains_point(points: list[Point2D], point: Point2D) -> bool:
    return any(
        abs(existing.x - point.x) <= 1.0e-9 and abs(existing.y - point.y) <= 1.0e-9
        for existing in points
    )
