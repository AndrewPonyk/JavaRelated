package org.computationalgeometry.algorithms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.model.Triangle2D;
import org.computationalgeometry.util.NumericPredicates;

public final class VoronoiDiagram {
    private VoronoiDiagram() {
    }

    public static final class Cell {
        private final Point2D site;
        private final List<Point2D> vertices;

        public Cell(Point2D site, List<Point2D> vertices) {
            this.site = site;
            this.vertices = List.copyOf(vertices);
        }

        public Point2D site() {
            return site;
        }

        public List<Point2D> vertices() {
            return vertices;
        }

        @Override
        public String toString() {
            return "site=" + site + ", vertices=" + vertices;
        }
    }

    public static List<Cell> build(List<Point2D> sites) {
        List<Point2D> uniqueSites = new ArrayList<>(new TreeSet<>(sites));
        Map<Point2D, List<Point2D>> centersBySite = new LinkedHashMap<>();
        for (Point2D site : uniqueSites) {
            centersBySite.put(site, new ArrayList<>());
        }

        for (Triangle2D triangle : DelaunayTriangulation.triangulate(uniqueSites)) {
            Point2D center = NumericPredicates.circumcenter(triangle.a(), triangle.b(), triangle.c());
            if (center == null) {
                continue;
            }
            addCenter(centersBySite.get(triangle.a()), center);
            addCenter(centersBySite.get(triangle.b()), center);
            addCenter(centersBySite.get(triangle.c()), center);
        }

        List<Cell> cells = new ArrayList<>();
        for (Map.Entry<Point2D, List<Point2D>> entry : centersBySite.entrySet()) {
            Point2D site = entry.getKey();
            List<Point2D> vertices = entry.getValue();
            vertices.sort(Comparator.comparingDouble(point -> Math.atan2(point.y() - site.y(), point.x() - site.x())));
            cells.add(new Cell(site, vertices));
        }
        return List.copyOf(cells);
    }

    private static void addCenter(List<Point2D> centers, Point2D center) {
        for (Point2D existing : centers) {
            if (Math.abs(existing.x() - center.x()) <= NumericPredicates.DEFAULT_EPSILON
                    && Math.abs(existing.y() - center.y()) <= NumericPredicates.DEFAULT_EPSILON) {
                return;
            }
        }
        centers.add(center);
    }
}
