package com.parallelimage.core.fork;

import com.parallelimage.core.model.ImageJob;
import com.parallelimage.core.model.JobOutcome;

/**
 * Processes exactly one {@link ImageJob} end to end (decode → transform → encode).
 *
 * <p>This is the seam that keeps {@link BatchProcessingTask} free of I/O: the Level-1 task only
 * knows how to <em>split a list</em> and <em>merge results</em>. Everything about files, formats and
 * pixels lives behind this interface, which is why the fork/join logic can be unit-tested with a
 * lambda and no image on disk at all.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><b>Thread-safe / stateless.</b> One instance serves every worker.</li>
 *   <li><b>Total function.</b> Returns a {@link JobOutcome} for every input, including failures.
 *       Throwing is permitted only for {@link java.util.concurrent.CancellationException}; anything
 *       else escaping is a bug (the pool's uncaught-exception handler will log it loudly).</li>
 *   <li><b>May itself fork.</b> Implementations are expected to run the Level-2
 *       {@link TileProcessingAction} tree inside this call — that nesting is the whole point.</li>
 * </ul>
 */
@FunctionalInterface
public interface JobProcessor {

    JobOutcome process(ImageJob job);
}
