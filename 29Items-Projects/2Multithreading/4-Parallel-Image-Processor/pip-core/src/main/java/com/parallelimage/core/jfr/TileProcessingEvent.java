package com.parallelimage.core.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * One leaf of a {@link com.parallelimage.core.fork.TileProcessingAction} tree: a single
 * {@link com.parallelimage.core.pipeline.TileKernel} applied to a single
 * {@link com.parallelimage.core.model.Tile}.
 *
 * <p>Leaves are the highest-frequency event in the system — a large panorama can produce
 * thousands per batch — so this event carries only the fields needed to see the tile-size
 * distribution and per-kernel cost in JFR, and relies on JFR's own threshold/throttle settings
 * (see {@code config/jfr/pip-default.jfc}) to keep recording overhead bounded.
 */
@Label("Tile Processing")
@Category({"Parallel Image Processor", "Tiles"})
@Description("A single TileKernel applied to a single leaf Tile")
public class TileProcessingEvent extends Event {

    @Label("Kernel")
    public String kernelName;

    @Label("Tile Width")
    public int tileWidth;

    @Label("Tile Height")
    public int tileHeight;

    @Label("Pixel Count")
    public long pixelCount;
}
