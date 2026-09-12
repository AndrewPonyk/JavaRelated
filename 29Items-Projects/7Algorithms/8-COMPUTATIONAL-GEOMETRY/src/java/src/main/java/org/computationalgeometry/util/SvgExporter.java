package org.computationalgeometry.util;

import java.util.List;
import org.computationalgeometry.model.Point2D;

public final class SvgExporter {
    private SvgExporter() {
    }

    public static String pointsAndPolyline(List<Point2D> points, List<Point2D> polyline, int width, int height) {
        Bounds bounds = Bounds.of(points);
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\" viewBox=\"0 0 ")
                .append(width)
                .append(' ')
                .append(height)
                .append("\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
        if (!polyline.isEmpty()) {
            svg.append("<polyline fill=\"none\" stroke=\"#2563eb\" stroke-width=\"2\" points=\"");
            for (Point2D point : polyline) {
                svg.append(project(point, bounds, width, height)).append(' ');
            }
            svg.append(project(polyline.get(0), bounds, width, height)).append("\"/>\n");
        }
        for (Point2D point : points) {
            svg.append("<circle cx=\"")
                    .append(projectX(point, bounds, width))
                    .append("\" cy=\"")
                    .append(projectY(point, bounds, height))
                    .append("\" r=\"3\" fill=\"#111827\"/>\n");
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private static String project(Point2D point, Bounds bounds, int width, int height) {
        return projectX(point, bounds, width) + "," + projectY(point, bounds, height);
    }

    private static double projectX(Point2D point, Bounds bounds, int width) {
        return 20.0 + ((point.x() - bounds.minX) / bounds.spanX()) * (width - 40.0);
    }

    private static double projectY(Point2D point, Bounds bounds, int height) {
        return height - 20.0 - ((point.y() - bounds.minY) / bounds.spanY()) * (height - 40.0);
    }

    private static final class Bounds {
        private final double minX;
        private final double maxX;
        private final double minY;
        private final double maxY;

        private Bounds(double minX, double maxX, double minY, double maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        private static Bounds of(List<Point2D> points) {
            double minX = points.stream().mapToDouble(Point2D::x).min().orElse(0.0);
            double maxX = points.stream().mapToDouble(Point2D::x).max().orElse(1.0);
            double minY = points.stream().mapToDouble(Point2D::y).min().orElse(0.0);
            double maxY = points.stream().mapToDouble(Point2D::y).max().orElse(1.0);
            return new Bounds(minX, maxX, minY, maxY);
        }

        private double spanX() {
            return Math.max(1.0e-9, maxX - minX);
        }

        private double spanY() {
            return Math.max(1.0e-9, maxY - minY);
        }
    }
}
