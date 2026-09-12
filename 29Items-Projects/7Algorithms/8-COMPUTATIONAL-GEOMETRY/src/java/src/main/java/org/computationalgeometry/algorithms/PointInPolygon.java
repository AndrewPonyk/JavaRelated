package org.computationalgeometry.algorithms;

import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.model.Polygon2D;
import org.computationalgeometry.util.NumericPredicates;

public final class PointInPolygon {
    private PointInPolygon() {
    }

    public enum Location {
        INSIDE,
        OUTSIDE,
        BOUNDARY
    }

    public static Location locate(Polygon2D polygon, Point2D point) {
        boolean inside = false;
        var vertices = polygon.vertices();

        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            Point2D a = vertices.get(j);
            Point2D b = vertices.get(i);
            if (NumericPredicates.onSegment(a, b, point, NumericPredicates.DEFAULT_EPSILON)) {
                return Location.BOUNDARY;
            }

            boolean crosses = (a.y() > point.y()) != (b.y() > point.y());
            if (crosses) {
                double xAtY = (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x();
                if (point.x() < xAtY) {
                    inside = !inside;
                }
            }
        }

        return inside ? Location.INSIDE : Location.OUTSIDE;
    }
}
