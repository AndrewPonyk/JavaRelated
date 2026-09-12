package com.parallelimage.core.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

/**
 * One call to {@link com.parallelimage.core.engine.ImageProcessingEngine#process}, from submission
 * to the merged {@link com.parallelimage.core.model.BatchResult}.
 *
 * <p>Started with {@link #begin()} before {@code pool.invoke(task)} and committed with
 * {@link #commit()} once the result is known, so the JFR-recorded duration is the batch's true
 * wall-clock time, not just the time spent building this event.
 */
@Label("Batch Processing")
@Category({"Parallel Image Processor", "Batches"})
@Description("A batch of images submitted to the fork/join pool as one BatchProcessingTask")
public class BatchProcessingEvent extends Event {

    @Label("Batch ID")
    public String batchId;

    @Label("Jobs Submitted")
    public int jobCount;

    @Label("Succeeded")
    public int succeeded;

    @Label("Failed")
    public int failed;

    @Label("Cancelled")
    public int cancelled;

    @Label("Pixels Processed")
    @Description("Summed pixel count of successfully processed images")
    public long pixelsProcessed;
}
