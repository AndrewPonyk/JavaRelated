package org.computationalgeometry.cli;

import java.io.IOException;
import java.util.List;
import org.computationalgeometry.algorithms.ClosestPair;
import org.computationalgeometry.algorithms.DelaunayTriangulation;
import org.computationalgeometry.algorithms.GrahamScan;
import org.computationalgeometry.algorithms.LineSweepIntersections;
import org.computationalgeometry.algorithms.PointInPolygon;
import org.computationalgeometry.algorithms.VoronoiDiagram;
import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.model.Polygon2D;
import org.computationalgeometry.model.Segment2D;
import org.computationalgeometry.util.ConsoleFormatter;
import org.computationalgeometry.util.InputLoader;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        int maxPoints = maxPointsFromEnv();
        List<Point2D> points = options.inputFile == null
                ? defaultPoints()
                : InputLoader.loadPoints(options.inputFile, maxPoints);

        System.out.println("Computational Geometry Demo - Java");
        System.out.println("==================================");
        ConsoleFormatter.printList("Input points", points);

        List<Point2D> hull = List.of();
        if (options.shouldRun("hull") || options.shouldRun("polygon")) {
            hull = GrahamScan.convexHull(points);
            ConsoleFormatter.printList("Convex hull", hull);
        }

        if (options.shouldRun("closest")) {
            var closest = ClosestPair.find(points);
            System.out.printf("Closest pair: %s <-> %s, distance %.3f%n",
                    closest.first(), closest.second(), closest.distance());
        }

        if (options.shouldRun("polygon") && hull.size() >= 3) {
            Polygon2D polygon = new Polygon2D(hull);
            Point2D query = new Point2D(1, 1);
            System.out.println("Point in polygon for " + query + ": " + PointInPolygon.locate(polygon, query));
        }

        if (options.shouldRun("sweep")) {
            List<Segment2D> segments = List.of(
                    new Segment2D(new Point2D(0, 0), new Point2D(2, 2)),
                    new Segment2D(new Point2D(0, 2), new Point2D(2, 0)),
                    new Segment2D(new Point2D(3, 0), new Point2D(3, 2)));
            ConsoleFormatter.printList("Segment intersections", LineSweepIntersections.findIntersections(segments));
        }

        if (options.shouldRun("delaunay")) {
            ConsoleFormatter.printList("Delaunay triangles", DelaunayTriangulation.triangulate(points));
        }

        if (options.shouldRun("voronoi")) {
            ConsoleFormatter.printList("Voronoi cells", VoronoiDiagram.build(points));
        }
    }

    private static List<Point2D> defaultPoints() {
        return List.of(
                new Point2D(0, 0),
                new Point2D(1, 2),
                new Point2D(2, 1),
                new Point2D(0.5, 1),
                new Point2D(2, 0),
                new Point2D(1, -0.5));
    }

    private static int maxPointsFromEnv() {
        String rawValue = System.getenv().getOrDefault("GEOMETRY_MAX_INPUT_POINTS", "1000000");
        try {
            int value = Integer.parseInt(rawValue);
            if (value < 1) {
                throw new IllegalArgumentException("GEOMETRY_MAX_INPUT_POINTS must be at least 1");
            }
            return value;
        } catch (NumberFormatException exc) {
            throw new IllegalArgumentException("GEOMETRY_MAX_INPUT_POINTS must be an integer", exc);
        }
    }

    private static final class Options {
        private final String algorithm;
        private final String inputFile;

        private Options(String algorithm, String inputFile) {
            this.algorithm = algorithm;
            this.inputFile = inputFile;
        }

        private boolean shouldRun(String name) {
            return "all".equals(algorithm) || name.equals(algorithm);
        }

        private static Options parse(String[] args) {
            String algorithm = "all";
            String inputFile = null;
            for (int i = 0; i < args.length; i++) {
                if ("--algorithm".equals(args[i]) && i + 1 < args.length) {
                    algorithm = args[++i];
                } else if ("--input".equals(args[i]) && i + 1 < args.length) {
                    inputFile = args[++i];
                } else {
                    throw new IllegalArgumentException("unknown or incomplete argument: " + args[i]);
                }
            }
            if (!List.of("all", "hull", "closest", "polygon", "sweep", "delaunay", "voronoi").contains(algorithm)) {
                throw new IllegalArgumentException("unsupported algorithm: " + algorithm);
            }
            return new Options(algorithm, inputFile);
        }
    }
}
