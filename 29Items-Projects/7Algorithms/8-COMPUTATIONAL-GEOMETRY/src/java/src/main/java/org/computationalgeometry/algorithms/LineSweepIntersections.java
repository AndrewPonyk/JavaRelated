package org.computationalgeometry.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.computationalgeometry.model.Segment2D;
import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.util.NumericPredicates;

public final class LineSweepIntersections {
    private LineSweepIntersections() {
    }

    public static final class Intersection {
        private final Segment2D first;
        private final Segment2D second;
        private final Point2D point;
        private final String kind;

        public Intersection(Segment2D first, Segment2D second, Point2D point, String kind) {
            this.first = first;
            this.second = second;
            this.point = point;
            this.kind = kind;
        }

        public Segment2D first() {
            return first;
        }

        public Segment2D second() {
            return second;
        }

        public Point2D point() {
            return point;
        }

        public String kind() {
            return kind;
        }

        @Override
        public String toString() {
            if (point != null) {
                return first + " intersects " + second + " at " + point;
            }
            return first + " overlaps " + second;
        }
    }

    public static List<Intersection> findIntersections(List<Segment2D> segments) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment2D segment = segments.get(i);
            events.add(new Event(Math.min(segment.start().x(), segment.end().x()), 0, i, segment));
            events.add(new Event(Math.max(segment.start().x(), segment.end().x()), 1, i, segment));
        }
        events.sort((a, b) -> {
            int byX = Double.compare(a.x, b.x);
            if (byX != 0) {
                return byX;
            }
            int byType = Integer.compare(a.type, b.type);
            if (byType != 0) {
                return byType;
            }
            return Integer.compare(a.index, b.index);
        });

        List<Intersection> intersections = new ArrayList<>();
        Map<Integer, Segment2D> active = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (Event event : events) {
            if (event.type == 0) {
                for (Map.Entry<Integer, Segment2D> entry : active.entrySet()) {
                    int firstIndex = Math.min(event.index, entry.getKey());
                    int secondIndex = Math.max(event.index, entry.getKey());
                    String key = firstIndex + ":" + secondIndex;
                    if (seen.contains(key)) {
                        continue;
                    }
                    Intersection intersection = intersection(event.segment, entry.getValue());
                    if (intersection != null) {
                        intersections.add(intersection);
                        seen.add(key);
                    }
                }
                active.put(event.index, event.segment);
            } else {
                active.remove(event.index);
            }
        }
        return List.copyOf(intersections);
    }

    public static Intersection intersection(Segment2D first, Segment2D second) {
        var a = first.start();
        var b = first.end();
        var c = second.start();
        var d = second.end();
        double eps = NumericPredicates.DEFAULT_EPSILON;

        int o1 = NumericPredicates.sign(NumericPredicates.orientation(a, b, c), eps);
        int o2 = NumericPredicates.sign(NumericPredicates.orientation(a, b, d), eps);
        int o3 = NumericPredicates.sign(NumericPredicates.orientation(c, d, a), eps);
        int o4 = NumericPredicates.sign(NumericPredicates.orientation(c, d, b), eps);

        if (o1 != o2 && o3 != o4) {
            return new Intersection(first, second, intersectionPoint(a, b, c, d), "point");
        }

        List<Point2D> boundaryPoints = new ArrayList<>();
        addBoundaryPoint(boundaryPoints, a, c, d, eps);
        addBoundaryPoint(boundaryPoints, b, c, d, eps);
        addBoundaryPoint(boundaryPoints, c, a, b, eps);
        addBoundaryPoint(boundaryPoints, d, a, b, eps);
        if (boundaryPoints.size() == 1) {
            return new Intersection(first, second, boundaryPoints.get(0), "point");
        }
        if (boundaryPoints.size() > 1) {
            return new Intersection(first, second, null, "overlap");
        }
        return null;
    }

    private static void addBoundaryPoint(List<Point2D> points, Point2D point, Point2D start, Point2D end, double eps) {
        if (NumericPredicates.onSegment(start, end, point, eps) && !points.contains(point)) {
            points.add(point);
        }
    }

    private static Point2D intersectionPoint(Point2D a, Point2D b, Point2D c, Point2D d) {
        double x1 = a.x();
        double y1 = a.y();
        double x2 = b.x();
        double y2 = b.y();
        double x3 = c.x();
        double y3 = c.y();
        double x4 = d.x();
        double y4 = d.y();
        double denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(denominator) <= NumericPredicates.DEFAULT_EPSILON) {
            return a;
        }
        double px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denominator;
        double py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denominator;
        return new Point2D(px, py);
    }

    private static final class Event {
        private final double x;
        private final int type;
        private final int index;
        private final Segment2D segment;

        private Event(double x, int type, int index, Segment2D segment) {
            this.x = x;
            this.type = type;
            this.index = index;
            this.segment = segment;
        }
    }
}
