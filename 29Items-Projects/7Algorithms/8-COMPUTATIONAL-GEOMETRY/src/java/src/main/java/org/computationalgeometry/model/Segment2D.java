package org.computationalgeometry.model;

import java.util.Objects;

public final class Segment2D {
    private final Point2D start;
    private final Point2D end;

    public Segment2D(Point2D start, Point2D end) {
        Objects.requireNonNull(start, "segment start must not be null");
        Objects.requireNonNull(end, "segment end must not be null");
        if (start.equals(end)) {
            throw new IllegalArgumentException("segment endpoints must be distinct");
        }
        this.start = start;
        this.end = end;
    }

    public Point2D start() {
        return start;
    }

    public Point2D end() {
        return end;
    }

    @Override
    public String toString() {
        return start + " -> " + end;
    }
}
