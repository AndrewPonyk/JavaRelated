package org.computationalgeometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.computationalgeometry.algorithms.ClosestPair;
import org.computationalgeometry.algorithms.DelaunayTriangulation;
import org.computationalgeometry.algorithms.GrahamScan;
import org.computationalgeometry.algorithms.LineSweepIntersections;
import org.computationalgeometry.algorithms.PointInPolygon;
import org.computationalgeometry.algorithms.VoronoiDiagram;
import org.computationalgeometry.cli.Main;
import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.model.Polygon2D;
import org.computationalgeometry.model.Segment2D;
import org.computationalgeometry.model.Triangle2D;
import org.computationalgeometry.util.InputLoader;
import org.computationalgeometry.util.NumericPredicates;
import org.junit.jupiter.api.Test;

class GeometryAlgorithmsTest {
    @Test
    void numericPredicatesCoverOrientationAreaAndCircumcenter() {
        Point2D a = new Point2D(0, 0);
        Point2D b = new Point2D(1, 0);
        Point2D c = new Point2D(0, 1);

        assertEquals(1, NumericPredicates.sign(NumericPredicates.orientation(a, b, c),
                NumericPredicates.DEFAULT_EPSILON));
        assertEquals(0.5, NumericPredicates.signedArea(List.of(a, b, c)), 1.0e-9);
        assertEquals(new Point2D(0.5, 0.5), NumericPredicates.circumcenter(a, b, c));
    }

    @Test
    void convexHullHandlesDuplicatesAndInteriorPoints() {
        List<Point2D> hull = GrahamScan.convexHull(List.of(
                new Point2D(0, 0),
                new Point2D(1, 0),
                new Point2D(1, 1),
                new Point2D(0, 1),
                new Point2D(0.5, 0.5),
                new Point2D(1, 1)));

        assertEquals(List.of(
                new Point2D(0, 0),
                new Point2D(1, 0),
                new Point2D(1, 1),
                new Point2D(0, 1)), hull);
    }

    @Test
    void convexHullCollinearPointsReturnEndpoints() {
        assertEquals(List.of(new Point2D(0, 0), new Point2D(2, 0)),
                GrahamScan.convexHull(List.of(new Point2D(0, 0), new Point2D(1, 0), new Point2D(2, 0))));
    }

    @Test
    void pointInPolygonDetectsInsideOutsideEdgeAndVertex() {
        Polygon2D square = new Polygon2D(List.of(
                new Point2D(0, 0),
                new Point2D(2, 0),
                new Point2D(2, 2),
                new Point2D(0, 2)));

        assertEquals(PointInPolygon.Location.INSIDE, PointInPolygon.locate(square, new Point2D(1, 1)));
        assertEquals(PointInPolygon.Location.OUTSIDE, PointInPolygon.locate(square, new Point2D(3, 1)));
        assertEquals(PointInPolygon.Location.BOUNDARY, PointInPolygon.locate(square, new Point2D(2, 1)));
        assertEquals(PointInPolygon.Location.BOUNDARY, PointInPolygon.locate(square, new Point2D(0, 0)));
    }

    @Test
    void closestPairHandlesDuplicatesAndFindsMinimumDistance() {
        ClosestPair.Result duplicate = ClosestPair.find(List.of(
                new Point2D(0, 0),
                new Point2D(2, 2),
                new Point2D(0, 0)));
        assertEquals(0.0, duplicate.distance(), 1.0e-9);

        ClosestPair.Result result = ClosestPair.find(List.of(
                new Point2D(0, 0),
                new Point2D(5, 5),
                new Point2D(2, 0),
                new Point2D(2, 1)));
        assertEquals(1.0, result.distance(), 1.0e-9);
        assertTrue(List.of(result.first(), result.second()).containsAll(List.of(new Point2D(2, 0), new Point2D(2, 1))));
    }

    @Test
    void lineSweepFindsCrossingTouchingOverlappingAndDisjointSegments() {
        LineSweepIntersections.Intersection crossing = LineSweepIntersections.intersection(
                new Segment2D(new Point2D(0, 0), new Point2D(2, 2)),
                new Segment2D(new Point2D(0, 2), new Point2D(2, 0)));
        LineSweepIntersections.Intersection touching = LineSweepIntersections.intersection(
                new Segment2D(new Point2D(0, 0), new Point2D(1, 1)),
                new Segment2D(new Point2D(1, 1), new Point2D(2, 2)));
        LineSweepIntersections.Intersection overlap = LineSweepIntersections.intersection(
                new Segment2D(new Point2D(0, 0), new Point2D(2, 0)),
                new Segment2D(new Point2D(1, 0), new Point2D(3, 0)));
        LineSweepIntersections.Intersection disjoint = LineSweepIntersections.intersection(
                new Segment2D(new Point2D(0, 0), new Point2D(1, 0)),
                new Segment2D(new Point2D(0, 1), new Point2D(1, 1)));

        assertNotNull(crossing);
        assertEquals(new Point2D(1, 1), crossing.point());
        assertNotNull(touching);
        assertEquals("point", touching.kind());
        assertNotNull(overlap);
        assertEquals("overlap", overlap.kind());
        assertNull(disjoint);
    }

    @Test
    void sweepRunnerReturnsExpectedIntersections() {
        List<LineSweepIntersections.Intersection> intersections = LineSweepIntersections.findIntersections(List.of(
                new Segment2D(new Point2D(0, 0), new Point2D(2, 2)),
                new Segment2D(new Point2D(0, 2), new Point2D(2, 0)),
                new Segment2D(new Point2D(3, 0), new Point2D(3, 2))));

        assertEquals(1, intersections.size());
        assertEquals(new Point2D(1, 1), intersections.get(0).point());
    }

    @Test
    void delaunayTriangulatesSquareAndRejectsCollinearInput() {
        List<Triangle2D> triangles = DelaunayTriangulation.triangulate(List.of(
                new Point2D(0, 0),
                new Point2D(1, 0),
                new Point2D(1, 1),
                new Point2D(0, 1)));

        assertEquals(2, triangles.size());
        assertTrue(triangles.stream()
                .allMatch(triangle -> new Point2D(0.5, 0.5).equals(DelaunayTriangulation.circumcenter(triangle))));
        assertTrue(DelaunayTriangulation.triangulate(List.of(
                new Point2D(0, 0),
                new Point2D(1, 0),
                new Point2D(2, 0))).isEmpty());
    }

    @Test
    void voronoiUsesDelaunayCircumcenters() {
        List<VoronoiDiagram.Cell> cells = VoronoiDiagram.build(List.of(
                new Point2D(0, 0),
                new Point2D(1, 0),
                new Point2D(0, 1)));

        assertEquals(3, cells.size());
        assertTrue(cells.stream().allMatch(cell -> cell.vertices().equals(List.of(new Point2D(0.5, 0.5)))));
    }

    @Test
    void inputLoaderReadsCsvAndJsonFixtures() throws Exception {
        List<Point2D> csvPoints = InputLoader.loadPoints("../../data/fixtures/default_points.csv");
        List<Point2D> jsonPoints = InputLoader.loadPoints("../../data/fixtures/default_points.json");

        assertEquals(csvPoints, jsonPoints);
        assertEquals(6, csvPoints.size());
    }

    @Test
    void inputLoaderRejectsUnknownFormat() {
        assertThrows(IllegalArgumentException.class, () -> InputLoader.loadPoints("../../README.md"));
    }

    @Test
    void inputLoaderRejectsInvalidAndOversizedInputs() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> InputLoader.loadPoints("../../data/fixtures/default_points.csv", 1));
        assertThrows(IllegalArgumentException.class,
                () -> InputLoader.loadPoints("../../data/fixtures/default_points.json", 1));
    }

    @Test
    void modelsRejectInvalidGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new Point2D(Double.NaN, 0));
        assertThrows(NullPointerException.class, () -> new Segment2D(null, new Point2D(0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Segment2D(new Point2D(0, 0), new Point2D(0, 0)));
        assertThrows(NullPointerException.class, () -> new Polygon2D(null));
        assertThrows(IllegalArgumentException.class,
                () -> new Polygon2D(List.of(new Point2D(0, 0), new Point2D(1, 0))));
    }

    @Test
    void javaCliRunsEndToEnd() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output));
        try {
            Main.main(new String[] {
                    "--algorithm", "hull",
                    "--input", "../../data/fixtures/default_points.csv"
            });
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString();
        assertTrue(text.contains("Computational Geometry Demo - Java"));
        assertTrue(text.contains("Convex hull (5)"));
    }
}
