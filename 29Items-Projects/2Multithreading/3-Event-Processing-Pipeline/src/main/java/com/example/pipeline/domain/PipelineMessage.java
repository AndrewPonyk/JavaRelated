package com.example.pipeline.domain;

/**
 * Anything that can travel through an inter-stage queue.
 *
 * <p>The hierarchy is <em>sealed</em> on purpose: it makes "is this a shutdown
 * signal or real work?" a compile-time-exhaustive pattern match instead of a
 * {@code null} check or an {@code instanceof} chain against a magic sentinel
 * object.
 *
 * <pre>{@code
 * switch (queue.poll(timeout)) {           // no default branch, by design
 *     case EventBatch batch -> process(batch);
 *     case PoisonPill pill  -> { running = false; }
 * }
 * }</pre>
 *
 * <p>Adding a third message type (say {@code FlushMarker}) will not compile until
 * every stage decides what to do with it — which is exactly the review the
 * change deserves. Never add a {@code default} branch to a switch over this
 * type: that silently re-opens the hole the seal closes.
 */
public sealed interface PipelineMessage permits EventBatch, PoisonPill {
}
