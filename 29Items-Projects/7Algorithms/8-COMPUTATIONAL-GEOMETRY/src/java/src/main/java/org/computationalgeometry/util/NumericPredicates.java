package org.computationalgeometry.util;

import org.computationalgeometry.model.Point2D;

public final class NumericPredicates {
    public static final double DEFAULT_EPSILON = 1.0e-9;

    private NumericPredicates() {
    }

    public static double orientation(Point2D a, Point2D b, Point2D c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    public static double signedArea(java.util.List<Point2D> points) {
        if (points.size() < 3) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < points.size(); i++) {
            Point2D current = points.get(i);
            Point2D next = points.get((i + 1) % points.size());
            total += current.x() * next.y() - current.y() * next.x();
        }
        return total / 2.0;
    }

    public static int sign(double value, double epsilon) {
        if (value > epsilon) {
            return 1;
        }
        if (value < -epsilon) {
            return -1;
        }
        return 0;
    }

    public static boolean onSegment(Point2D a, Point2D b, Point2D p, double epsilon) {
        return sign(orientation(a, b, p), epsilon) == 0
                && p.x() >= Math.min(a.x(), b.x()) - epsilon
                && p.x() <= Math.max(a.x(), b.x()) + epsilon
                && p.y() >= Math.min(a.y(), b.y()) - epsilon
                && p.y() <= Math.max(a.y(), b.y()) + epsilon;
    }

    public static Point2D circumcenter(Point2D a, Point2D b, Point2D c) {
        double denominator = 2.0 * (
                a.x() * (b.y() - c.y())
                        + b.x() * (c.y() - a.y())
                        + c.x() * (a.y() - b.y()));
        if (Math.abs(denominator) <= DEFAULT_EPSILON) {
            return null;
        }

        double a2 = a.x() * a.x() + a.y() * a.y();
        double b2 = b.x() * b.x() + b.y() * b.y();
        double c2 = c.x() * c.x() + c.y() * c.y();
        double x = (a2 * (b.y() - c.y()) + b2 * (c.y() - a.y()) + c2 * (a.y() - b.y())) / denominator;
        double y = (a2 * (c.x() - b.x()) + b2 * (a.x() - c.x()) + c2 * (b.x() - a.x())) / denominator;
        return new Point2D(x, y);
    }
}
