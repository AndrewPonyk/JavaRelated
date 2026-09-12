package org.computationalgeometry.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.model.Triangle2D;
import org.computationalgeometry.util.NumericPredicates;

public final class DelaunayTriangulation {
    private DelaunayTriangulation() {
    }

    public static List<Triangle2D> triangulate(List<Point2D> points) {
        List<Point2D> sorted = new ArrayList<>(new TreeSet<>(points));
        if (sorted.size() < 3 || allCollinear(sorted)) {
            return List.of();
        }

        Triangle2D superTriangle = buildSuperTriangle(sorted);
        List<Triangle2D> triangles = new ArrayList<>();
        triangles.add(superTriangle);

        for (Point2D point : sorted) {
            List<Triangle2D> badTriangles = new ArrayList<>();
            for (Triangle2D triangle : triangles) {
                if (inCircumcircle(triangle, point)) {
                    badTriangles.add(triangle);
                }
            }

            List<Edge> boundary = boundaryEdges(badTriangles);
            triangles.removeAll(badTriangles);
            for (Edge edge : boundary) {
                Triangle2D candidate = orientedTriangle(edge.first, edge.second, point);
                if (candidate != null) {
                    triangles.add(candidate);
                }
            }
        }

        List<Triangle2D> result = new ArrayList<>();
        for (Triangle2D triangle : triangles) {
            if (!containsSuperVertex(triangle, superTriangle)) {
                result.add(triangle);
            }
        }
        result.sort((left, right) -> triangleKey(left).compareTo(triangleKey(right)));
        return List.copyOf(result);
    }

    public static Point2D circumcenter(Triangle2D triangle) {
        return NumericPredicates.circumcenter(triangle.a(), triangle.b(), triangle.c());
    }

    private static Triangle2D buildSuperTriangle(List<Point2D> points) {
        double minX = points.stream().mapToDouble(Point2D::x).min().orElse(0.0);
        double maxX = points.stream().mapToDouble(Point2D::x).max().orElse(1.0);
        double minY = points.stream().mapToDouble(Point2D::y).min().orElse(0.0);
        double maxY = points.stream().mapToDouble(Point2D::y).max().orElse(1.0);
        double delta = Math.max(maxX - minX, maxY - minY);
        if (delta <= NumericPredicates.DEFAULT_EPSILON) {
            delta = 1.0;
        }
        double midX = (minX + maxX) / 2.0;
        double midY = (minY + maxY) / 2.0;
        double span = delta * 64.0;
        return new Triangle2D(
                new Point2D(midX - 2.0 * span, midY - span),
                new Point2D(midX, midY + 2.0 * span),
                new Point2D(midX + 2.0 * span, midY - span));
    }

    private static List<Edge> boundaryEdges(List<Triangle2D> triangles) {
        Map<Edge, Integer> counts = new HashMap<>();
        for (Triangle2D triangle : triangles) {
            for (Edge edge : edges(triangle)) {
                counts.merge(edge, 1, Integer::sum);
            }
        }
        List<Edge> boundary = new ArrayList<>();
        for (Map.Entry<Edge, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 1) {
                boundary.add(entry.getKey());
            }
        }
        Collections.sort(boundary);
        return boundary;
    }

    private static List<Edge> edges(Triangle2D triangle) {
        return List.of(
                new Edge(triangle.a(), triangle.b()),
                new Edge(triangle.b(), triangle.c()),
                new Edge(triangle.c(), triangle.a()));
    }

    private static boolean inCircumcircle(Triangle2D triangle, Point2D point) {
        Point2D center = circumcenter(triangle);
        if (center == null) {
            return false;
        }
        double radiusSquared = center.distanceSquared(triangle.a());
        return center.distanceSquared(point) <= radiusSquared + NumericPredicates.DEFAULT_EPSILON;
    }

    private static Triangle2D orientedTriangle(Point2D a, Point2D b, Point2D c) {
        int turn = NumericPredicates.sign(NumericPredicates.orientation(a, b, c), NumericPredicates.DEFAULT_EPSILON);
        if (turn == 0) {
            return null;
        }
        return turn > 0 ? new Triangle2D(a, b, c) : new Triangle2D(a, c, b);
    }

    private static boolean containsSuperVertex(Triangle2D triangle, Triangle2D superTriangle) {
        return vertexEquals(triangle.a(), superTriangle)
                || vertexEquals(triangle.b(), superTriangle)
                || vertexEquals(triangle.c(), superTriangle);
    }

    private static boolean vertexEquals(Point2D point, Triangle2D triangle) {
        return point.equals(triangle.a()) || point.equals(triangle.b()) || point.equals(triangle.c());
    }

    private static boolean allCollinear(List<Point2D> points) {
        Point2D first = points.get(0);
        Point2D second = points.get(1);
        for (int i = 2; i < points.size(); i++) {
            if (NumericPredicates.sign(NumericPredicates.orientation(first, second, points.get(i)),
                    NumericPredicates.DEFAULT_EPSILON) != 0) {
                return false;
            }
        }
        return true;
    }

    private static String triangleKey(Triangle2D triangle) {
        return pointKey(triangle.a()) + "|" + pointKey(triangle.b()) + "|" + pointKey(triangle.c());
    }

    private static String pointKey(Point2D point) {
        return point.x() + "," + point.y();
    }

    private static final class Edge implements Comparable<Edge> {
        private final Point2D first;
        private final Point2D second;

        private Edge(Point2D first, Point2D second) {
            if (second.compareTo(first) < 0) {
                this.first = second;
                this.second = first;
            } else {
                this.first = first;
                this.second = second;
            }
        }

        @Override
        public int compareTo(Edge other) {
            int byFirst = first.compareTo(other.first);
            if (byFirst != 0) {
                return byFirst;
            }
            return second.compareTo(other.second);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Edge)) {
                return false;
            }
            Edge edge = (Edge) other;
            return first.equals(edge.first) && second.equals(edge.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }
}
