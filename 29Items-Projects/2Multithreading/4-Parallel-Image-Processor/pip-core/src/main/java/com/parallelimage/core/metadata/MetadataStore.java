package com.parallelimage.core.metadata;

import com.parallelimage.core.model.ImageMetadata;
import com.parallelimage.core.util.Preconditions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

/**
 * Read-mostly store of per-job {@link ImageMetadata}, guarded by a {@link StampedLock}.
 *
 * <h2>Why {@code StampedLock} and not something simpler</h2>
 * The access pattern here is roughly 100 reads per write: every worker writes one metadata record
 * when it decodes an image, while the UI's status bar and progress panel read aggregate figures ~30
 * times a second, and the job detail pane reads individual records on selection. For that shape:
 * <ul>
 *   <li><b>{@code synchronized}</b> serializes readers against each other for no reason.</li>
 *   <li><b>{@code ReentrantReadWriteLock}</b> lets readers share, but every reader still performs a
 *       CAS on the shared state word — on a 16-core machine polling at 30 Hz that is measurable
 *       cache-line ping-pong, and its writer-starvation behaviour under sustained reads is worse.</li>
 *   <li><b>{@code StampedLock}</b> offers an <em>optimistic</em> read: take a stamp, read, then
 *       validate. On the (overwhelmingly common) uncontended path there is <strong>no write to shared
 *       memory at all</strong> — just two volatile reads. That is the cheapest correct read available
 *       on the JDK.</li>
 * </ul>
 *
 * <h2>What optimistic reads are used for here — and what they are deliberately not used for</h2>
 * This is the subtle part, and getting it wrong produces a hang rather than a wrong answer.
 *
 * <p>An optimistic read runs <em>concurrently with a writer</em>. Anything it touches must therefore
 * tolerate being observed mid-update. A {@code long} or {@code int} field does: it may be stale, which
 * {@link StampedLock#validate(long)} then detects. <strong>A {@link HashMap} does not.</strong>
 * Traversing a {@code HashMap} while another thread is resizing it can follow a half-rewired bin and
 * loop forever — a livelock that no amount of {@code validate()} afterwards can rescue, because the
 * read never returns to reach the {@code validate()} call.
 *
 * <p>So the split is:
 * <ul>
 *   <li><b>Aggregates</b> ({@link #stats()}) are plain scalar fields → optimistic read, pessimistic
 *       fallback. This is the 30 Hz path and the reason the class exists.</li>
 *   <li><b>Map lookups</b> ({@link #find}, {@link #snapshot()}) take a real
 *       {@link StampedLock#readLock() read lock}. Still shared and still cheap; just not optimistic.</li>
 * </ul>
 *
 * <h2>Rules for touching this class</h2>
 * <ol>
 *   <li><b>Never re-enter.</b> {@code StampedLock} is <em>not</em> reentrant. A method that holds the
 *       write lock and calls another public method of this class self-deadlocks. All private helpers
 *       here assume the lock is already held and say so.</li>
 *   <li><b>Never call foreign code while holding the lock</b> — no listeners, no logging with a
 *       computed message that could touch the store, no I/O. The
 *       {@link #computeIfAbsent(String, Supplier)} supplier is the one exception and is documented
 *       there.</li>
 *   <li><b>Values must be immutable.</b> {@link ImageMetadata} is a record with an unmodifiable EXIF
 *       map. That is load-bearing: an optimistic reader that grabs a reference and then loses
 *       validation must still be holding a <em>whole</em> object, merely a stale one.</li>
 *   <li><b>Always unlock in a {@code finally}.</b> {@code StampedLock} has no ownership tracking, so a
 *       leaked write stamp is unrecoverable for the lifetime of the JVM.</li>
 * </ol>
 *
 * <p><strong>Thread-safe.</strong>
 */
public final class MetadataStore {

    private final StampedLock lock = new StampedLock();

    /** Guarded by {@link #lock}. Never traversed under an optimistic stamp — see class javadoc. */
    private final Map<String, ImageMetadata> entries = new HashMap<>();

    // ---- aggregates: guarded by lock, but safe to read optimistically (plain scalars) ----
    private int count;
    private long totalPixels;
    private long totalSourceBytes;
    private long maxPixels;

    // ---- observability: intentionally outside the lock ----
    private final LongAdder optimisticHits = new LongAdder();
    private final LongAdder optimisticMisses = new LongAdder();

    /** Aggregate view of the store. Immutable, so it can be handed straight to the UI thread. */
    public record Stats(int count, long totalPixels, long totalSourceBytes, long maxPixels) {

        public static final Stats EMPTY = new Stats(0, 0L, 0L, 0L);

        public double averageMegapixels() {
            return count == 0 ? 0.0d : totalPixels / (double) count / 1_000_000.0d;
        }

        public double totalMegapixels() {
            return totalPixels / 1_000_000.0d;
        }
    }

    /**
     * Inserts or replaces the record for a job.
     *
     * <p>Exclusive: aggregates and the map must not be observed half-updated by a pessimistic reader.
     */
    public void put(ImageMetadata metadata) {
        Preconditions.requireNonNull(metadata, "metadata");
        long stamp = lock.writeLock();
        try {
            ImageMetadata previous = entries.put(metadata.jobId(), metadata);
            if (previous != null) {
                // Replacement, not insertion: back the old contribution out so the aggregates stay
                // exact. Recomputing from scratch would be O(n) on every write.
                count--;
                totalPixels -= previous.pixelCount();
                totalSourceBytes -= previous.sourceBytes();
            }
            count++;
            totalPixels += metadata.pixelCount();
            totalSourceBytes += metadata.sourceBytes();
            maxPixels = Math.max(maxPixels, metadata.pixelCount());
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Aggregate figures, read optimistically.
     *
     * <p>The canonical {@code StampedLock} idiom, and worth reading closely:
     * <ol>
     *   <li>{@link StampedLock#tryOptimisticRead()} returns a stamp without acquiring anything (it
     *       returns 0 if a write lock is currently held).</li>
     *   <li>The fields are copied into locals. This read may be racing a writer.</li>
     *   <li>{@link StampedLock#validate(long)} reports whether a write intervened. It also issues the
     *       acquire fence that makes step 2's reads safe to use.</li>
     *   <li>On failure — a genuine write happened during the read — fall back to a real read lock and
     *       do it again. This is not a spin loop: exactly one retry, guaranteed to terminate.</li>
     * </ol>
     * The four values are copied together so callers can never see, say, a {@code count} from after a
     * write paired with a {@code totalPixels} from before it.
     */
    public Stats stats() {
        long stamp = lock.tryOptimisticRead();
        int localCount = count;
        long localPixels = totalPixels;
        long localBytes = totalSourceBytes;
        long localMax = maxPixels;

        if (lock.validate(stamp)) {
            optimisticHits.increment();
            return new Stats(localCount, localPixels, localBytes, localMax);
        }

        optimisticMisses.increment();
        stamp = lock.readLock();
        try {
            return new Stats(count, totalPixels, totalSourceBytes, maxPixels);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Looks up one record.
     *
     * <p>Pessimistic read lock, on purpose — see the class javadoc on why traversing a {@code HashMap}
     * under an optimistic stamp risks a livelock rather than a stale answer. Shared read locks still
     * allow every reader through concurrently.
     */
    public Optional<ImageMetadata> find(String jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        long stamp = lock.readLock();
        try {
            return Optional.ofNullable(entries.get(jobId));
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Returns the existing record, or computes and stores one.
     *
     * <p>Demonstrates {@link StampedLock#tryConvertToWriteLock(long)}: start under a read lock (the
     * common case is a hit), and on a miss attempt an in-place upgrade. If the upgrade fails, the read
     * lock must be released before blocking for the write lock — and the check must then be
     * <em>repeated</em>, because another thread may have inserted the value in the gap. Forgetting
     * that recheck is the classic broken double-checked-locking bug.
     *
     * <p>The supplier runs while the write lock is held, which violates rule 2 above ("no foreign code
     * under the lock"). It is tolerated because the intended suppliers are pure constructors over
     * already-decoded values. Do not pass one that performs I/O.
     */
    public ImageMetadata computeIfAbsent(String jobId, Supplier<ImageMetadata> supplier) {
        Preconditions.requireNonBlank(jobId, "jobId");
        Preconditions.requireNonNull(supplier, "supplier");

        long stamp = lock.readLock();
        try {
            ImageMetadata existing = entries.get(jobId);
            if (existing != null) {
                return existing;
            }
            long writeStamp = lock.tryConvertToWriteLock(stamp);
            if (writeStamp == 0L) {
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                // Recheck: the value may have appeared while we were not holding anything.
                ImageMetadata raced = entries.get(jobId);
                if (raced != null) {
                    return raced;
                }
            } else {
                stamp = writeStamp;
            }
            ImageMetadata created = supplier.get();
            putLocked(jobId, created);
            return created;
        } finally {
            // Correct for either mode: unlock() dispatches on the stamp, which is why the single
            // `stamp` variable is reassigned rather than a second one being introduced.
            lock.unlock(stamp);
        }
    }

    /** Requires the write lock to be held by the caller. */
    private void putLocked(String jobId, ImageMetadata metadata) {
        Preconditions.requireNonNull(metadata, "metadata");
        entries.put(jobId, metadata);
        count++;
        totalPixels += metadata.pixelCount();
        totalSourceBytes += metadata.sourceBytes();
        maxPixels = Math.max(maxPixels, metadata.pixelCount());
    }

    /**
     * Immutable snapshot of every record, ordered by descending pixel count.
     *
     * <p>Sorting happens <em>after</em> the lock is released: an O(n log n) comparison sort is far too
     * much work to do while holding a lock that workers need in order to make progress.
     */
    public List<ImageMetadata> snapshot() {
        List<ImageMetadata> copy;
        long stamp = lock.readLock();
        try {
            copy = List.copyOf(entries.values());
        } finally {
            lock.unlockRead(stamp);
        }
        return copy.stream()
                .sorted((a, b) -> Long.compare(b.pixelCount(), a.pixelCount()))
                .toList();
    }

    public int size() {
        long stamp = lock.tryOptimisticRead();
        int localCount = count;
        if (lock.validate(stamp)) {
            optimisticHits.increment();
            return localCount;
        }
        optimisticMisses.increment();
        stamp = lock.readLock();
        try {
            return count;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** Drops everything. Used between batches so memory does not grow across a long UI session. */
    public void clear() {
        long stamp = lock.writeLock();
        try {
            entries.clear();
            count = 0;
            totalPixels = 0L;
            totalSourceBytes = 0L;
            maxPixels = 0L;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Ratio of optimistic reads that validated, as a percentage.
     *
     * <p>Exposed because it is the one number that tells you whether choosing {@code StampedLock} was
     * justified. Below ~90% the write rate is high enough that a plain read/write lock would be
     * simpler and no slower; if you see that, revisit the design rather than the lock.
     */
    public double optimisticSuccessRate() {
        long hits = optimisticHits.sum();
        long total = hits + optimisticMisses.sum();
        return total == 0L ? 1.0d : hits / (double) total;
    }

    @Override
    public String toString() {
        Stats s = stats();
        return "MetadataStore[entries=%d totalMP=%.1f optimistic=%.1f%%]".formatted(
                s.count(), s.totalMegapixels(), optimisticSuccessRate() * 100.0d);
    }
}
