package com.example.pipeline.infrastructure.sink;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.pipeline.application.port.AggregateSink;
import com.example.pipeline.domain.AggregateSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Fan-out semantics, and specifically what happens when one delegate throws.
 *
 * <p>The test that matters is {@link #aFailingSinkDoesNotStopTheOthers()}: a CSV write onto
 * a read-only directory must still leave the console table on screen. The naive
 * implementation — a plain loop with no {@code try} — loses every sink after the failing
 * one, and the loss is invisible because the exception looks like a perfectly ordinary CSV
 * error.
 *
 * <p>Recording sinks rather than a mocking framework: each one is three lines, the
 * assertions read as English, and the project has no test-scope dependencies beyond JUnit.
 */
@Timeout(10)
@DisplayName("CompositeAggregateSink")
class CompositeAggregateSinkTest {

    /** Remembers every snapshot it was handed. */
    private static final class RecordingSink implements AggregateSink {
        private final List<AggregateSnapshot> received = new ArrayList<>();

        @Override
        public void publish(AggregateSnapshot snapshot) {
            received.add(snapshot);
        }
    }

    /** Throws the exception it was constructed with, every time. */
    private static final class ThrowingSink implements AggregateSink {
        private final RuntimeException failure;

        ThrowingSink(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void publish(AggregateSnapshot snapshot) {
            throw failure;
        }
    }

    private static final AggregateSnapshot SNAPSHOT = AggregateSnapshot.empty();

    @Test
    @DisplayName("null and empty delegate lists are rejected at construction")
    void degenerateDelegateListsAreRejected() {
        assertThrows(NullPointerException.class, () -> new CompositeAggregateSink(null));
        assertThrows(IllegalArgumentException.class, () -> new CompositeAggregateSink(List.of()),
                "an empty composite would silently discard every report");
    }

    @Test
    @DisplayName("a null snapshot is rejected before any delegate is called")
    void nullSnapshotIsRejected() {
        RecordingSink recording = new RecordingSink();
        assertThrows(NullPointerException.class,
                () -> new CompositeAggregateSink(List.of(recording)).publish(null));
        assertEquals(0, recording.received.size(), "no delegate should see a partial call");
    }

    @Test
    @DisplayName("every delegate receives the same snapshot instance")
    void everyDelegateGetsTheSnapshot() {
        RecordingSink first = new RecordingSink();
        RecordingSink second = new RecordingSink();
        new CompositeAggregateSink(List.of(first, second)).publish(SNAPSHOT);

        assertEquals(1, first.received.size());
        assertEquals(1, second.received.size());
        // Same instance, not a copy: the snapshot is immutable, so copying would be waste.
        assertSame(SNAPSHOT, first.received.get(0));
        assertSame(SNAPSHOT, second.received.get(0));
    }

    @Test
    @DisplayName("the delegate list is copied, so a later mutation cannot change the fan-out")
    void delegateListIsCopied() {
        List<AggregateSink> mutable = new ArrayList<>(List.of(new RecordingSink()));
        CompositeAggregateSink composite = new CompositeAggregateSink(mutable);
        mutable.clear();
        assertEquals(1, composite.size());
    }

    @Test
    @DisplayName("of() builds a composite from one sink plus varargs")
    void ofFactoryBuildsTheComposite() {
        assertEquals(1, CompositeAggregateSink.of(new RecordingSink()).size());
        assertEquals(3, CompositeAggregateSink.of(new RecordingSink(), new RecordingSink(),
                new RecordingSink()).size());
    }

    @Test
    @DisplayName("of() rejects nulls rather than fanning out to a null delegate")
    void ofFactoryRejectsNulls() {
        assertThrows(NullPointerException.class, () -> CompositeAggregateSink.of(null));
        assertThrows(NullPointerException.class,
                () -> CompositeAggregateSink.of(new RecordingSink(), (AggregateSink) null));
        assertThrows(NullPointerException.class,
                () -> CompositeAggregateSink.of(new RecordingSink(), (AggregateSink[]) null));
    }

    /**
     * The whole reason the {@code try} block inside the loop exists. A failure in the middle
     * delegate must not cost the run its console table, which is the only output an operator
     * watching the terminal ever sees.
     */
    @Test
    @DisplayName("a failing sink does not stop the sinks after it")
    void aFailingSinkDoesNotStopTheOthers() {
        RecordingSink before = new RecordingSink();
        RecordingSink after = new RecordingSink();
        IllegalStateException boom = new IllegalStateException("csv directory is read-only");

        CompositeAggregateSink composite =
                new CompositeAggregateSink(List.of(before, new ThrowingSink(boom), after));

        assertSame(boom, assertThrows(IllegalStateException.class, () -> composite.publish(SNAPSHOT)),
                "the first failure is what the caller sees");
        assertEquals(1, before.received.size());
        assertEquals(1, after.received.size(), "the sink after the failing one must still run");
    }

    /**
     * Two independent failures are two separate diagnoses, so neither may be dropped.
     * Suppression is the JDK's own idiom for exactly this and keeps both stack traces in
     * whatever prints the throwable.
     */
    @Test
    @DisplayName("a second failure is suppressed onto the first rather than lost")
    void laterFailuresAreSuppressedOntoTheFirst() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");

        CompositeAggregateSink composite =
                new CompositeAggregateSink(List.of(new ThrowingSink(first), new ThrowingSink(second)));

        RuntimeException thrown = assertThrows(IllegalStateException.class,
                () -> composite.publish(SNAPSHOT));
        assertSame(first, thrown);
        assertEquals(1, thrown.getSuppressed().length, "the second failure must be reachable");
        assertSame(second, thrown.getSuppressed()[0]);
    }

    @Test
    @DisplayName("all delegates failing still reports only one exception, carrying the rest")
    void everyDelegateFailing() {
        IllegalStateException first = new IllegalStateException("a");
        CompositeAggregateSink composite = new CompositeAggregateSink(List.of(
                new ThrowingSink(first),
                new ThrowingSink(new IllegalStateException("b")),
                new ThrowingSink(new IllegalStateException("c"))));

        RuntimeException thrown = assertThrows(IllegalStateException.class,
                () -> composite.publish(SNAPSHOT));
        assertEquals(2, thrown.getSuppressed().length, "b and c must both be attached");
    }

    @Test
    @DisplayName("a run where nothing fails throws nothing")
    void healthyFanOutIsSilent() {
        CompositeAggregateSink composite = new CompositeAggregateSink(
                List.of(new RecordingSink(), AggregateSink.discarding(), new RecordingSink()));
        assertDoesNotThrow(() -> composite.publish(SNAPSHOT));
    }

    @Test
    @DisplayName("delegates are visited in the order given")
    void orderIsPreserved() {
        List<String> order = new ArrayList<>();
        AggregateSink first = snapshot -> order.add("first");
        AggregateSink second = snapshot -> order.add("second");
        new CompositeAggregateSink(List.of(first, second)).publish(SNAPSHOT);
        // The console sink is registered first on purpose, so the terminal shows the table
        // even when a slower file sink is still writing.
        assertEquals(List.of("first", "second"), order);
    }
}
