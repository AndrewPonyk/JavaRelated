package com.example.pipeline.domain;

/**
 * The shutdown sentinel: "no more work will follow me on this queue".
 *
 * <p>A pill is a <em>drain</em> request, unlike {@code shutdownNow()} which is a
 * <em>cancel</em>. Because {@code ArrayBlockingQueue} is FIFO, a pill enqueued
 * after the last batch can never overtake real work, so every event already in
 * flight is processed before any consumer exits. That property is what lets the
 * pipeline assert {@code produced == passed + rejected} at the end.
 *
 * <p><strong>Counting rules</strong> (the single most common source of hangs in
 * this pattern):
 * <ul>
 *   <li>{@code N} consumers require exactly {@code N} pills — one pill wakes one
 *       consumer, and the others would block until the shutdown timeout.</li>
 *   <li>A consumer must never re-enqueue the pill it consumed onto the queue it
 *       read from: with a full queue that is an immediate deadlock.</li>
 *   <li>The next stage gets exactly <em>one</em> pill, forwarded by the last
 *       consumer to finish; one pill per consumer would stop the next stage at the
 *       first, discarding in-flight batches.</li>
 * </ul>
 *
 * <p>A singleton because it carries no state and identity is irrelevant — stages
 * match on the type, never on the instance.
 */
public final class PoisonPill implements PipelineMessage {

    /** The one and only pill instance. */
    public static final PoisonPill INSTANCE = new PoisonPill();

    private PoisonPill() {
    }

    @Override
    public String toString() {
        return "PoisonPill";
    }
}
