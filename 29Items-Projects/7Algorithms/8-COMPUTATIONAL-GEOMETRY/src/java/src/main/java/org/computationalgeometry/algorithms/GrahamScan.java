package org.computationalgeometry.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.computationalgeometry.model.Point2D;
import org.computationalgeometry.util.NumericPredicates;

public final class GrahamScan {
    private GrahamScan() {
    }

    public static List<Point2D> convexHull(List<Point2D> input) {
        TreeSet<Point2D> unique = new TreeSet<>(input);
        if (unique.size() <= 1) {
            return List.copyOf(unique);
        }

        List<Point2D> points = new ArrayList<>(unique);
        List<Point2D> lower = new ArrayList<>();
        for (Point2D point : points) {
            while (lower.size() >= 2 && NumericPredicates.orientation(
                    lower.get(lower.size() - 2),
                    lower.get(lower.size() - 1),
                    point) <= NumericPredicates.DEFAULT_EPSILON) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<Point2D> upper = new ArrayList<>();
        Collections.reverse(points);
        for (Point2D point : points) {
            while (upper.size() >= 2 && NumericPredicates.orientation(
                    upper.get(upper.size() - 2),
                    upper.get(upper.size() - 1),
                    point) <= NumericPredicates.DEFAULT_EPSILON) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return List.copyOf(lower);
    }
}
