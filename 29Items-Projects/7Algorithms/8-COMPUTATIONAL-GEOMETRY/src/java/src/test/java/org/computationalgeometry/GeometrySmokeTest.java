package org.computationalgeometry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.computationalgeometry.algorithms.GrahamScan;
import org.computationalgeometry.model.Point2D;
import org.junit.jupiter.api.Test;

class GeometrySmokeTest {
    @Test
    void convexHullDemoInputProducesBoundary() {
        List<Point2D> hull = GrahamScan.convexHull(List.of(
                new Point2D(0, 0),
                new Point2D(1, 2),
                new Point2D(2, 1),
                new Point2D(0.5, 1),
                new Point2D(2, 0),
                new Point2D(1, -0.5)));

        assertTrue(hull.size() >= 3);
    }
}
