package com.parallelimage.core.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TileBufferPoolTest {

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 4",
        "4, 4",
        "5, 8",
        "6, 8",
        "8, 8",
        "9, 16",
        "17, 32",
    })
    @DisplayName("borrow rounds minLength up to the next power of two")
    void borrowRoundsUpToNextPowerOfTwo(int minLength, int expectedLength) {
        int[] array = TileBufferPool.borrow(minLength);

        assertEquals(expectedLength, array.length);
    }

    @Test
    @DisplayName("borrow clamps a non-positive length to a bucket of size 1")
    void borrowClampsNonPositiveLengthToOne() {
        assertEquals(1, TileBufferPool.borrow(0).length);
        assertEquals(1, TileBufferPool.borrow(-5).length);
    }

    @Test
    @DisplayName("borrow clamps huge requests to the largest power-of-two bucket that fits in an int")
    void borrowClampsHugeRequestsToMaxBucket() throws ReflectiveOperationException {
        // bucketFor() is exercised via reflection rather than through borrow(): the real bucket-30
        // array is ~4 GiB, and allocating that (four times over) is exactly the kind of thing that
        // starves the surefire-forked JVM's heap without proving anything borrow(100) doesn't already.
        Method bucketFor = TileBufferPool.class.getDeclaredMethod("bucketFor", int.class);
        bucketFor.setAccessible(true);
        int maxBucket = 30;

        assertEquals(maxBucket, bucketFor.invoke(null, 1 << 30));
        assertEquals(maxBucket, bucketFor.invoke(null, (1 << 30) - 1));
        assertEquals(maxBucket, bucketFor.invoke(null, (1 << 30) / 2 + 1));
        assertEquals(maxBucket, bucketFor.invoke(null, Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("an array released and then borrowed again for the same bucket is the same instance")
    void releaseThenBorrowReusesTheSameArray() {
        int[] borrowed = TileBufferPool.borrow(100);

        TileBufferPool.release(borrowed);
        int[] reused = TileBufferPool.borrow(100);

        assertSame(borrowed, reused);
    }

    @Test
    @DisplayName("an array whose length is not a power of two is dropped, not pooled")
    void releaseDropsNonPowerOfTwoLengthArrays() {
        int[] notPowerOfTwo = new int[100];

        TileBufferPool.release(notPowerOfTwo);
        int[] borrowed = TileBufferPool.borrow(100);

        assertNotSame(notPowerOfTwo, borrowed);
    }

    @Test
    @DisplayName("releasing null is a silent no-op")
    void releaseNullIsNoOp() {
        TileBufferPool.release(null);
    }

    @Test
    @DisplayName("a bucket holds at most 4 arrays: releases beyond that are dropped, and reuse is LIFO")
    void bucketCapIsFourAndReuseOrderIsLifo() {
        // TileBufferPool is thread-confined but its state outlives any one test: other test classes
        // in this same forked JVM (e.g. BoxBlurFilterTest, which really applies a blur with tw=4 tiles)
        // run on this same thread and can leave arrays sitting in this very bucket. Drain it first so
        // the LIFO assertions below aren't at the mercy of test execution order.
        for (int i = 0; i < 8; i++) {
            TileBufferPool.borrow(4);
        }

        int[] first = TileBufferPool.borrow(4);
        int[] second = new int[first.length];
        int[] third = new int[first.length];
        int[] fourth = new int[first.length];
        int[] fifth = new int[first.length];

        TileBufferPool.release(first);
        TileBufferPool.release(second);
        TileBufferPool.release(third);
        TileBufferPool.release(fourth);
        TileBufferPool.release(fifth);

        int[] r1 = TileBufferPool.borrow(4);
        int[] r2 = TileBufferPool.borrow(4);
        int[] r3 = TileBufferPool.borrow(4);
        int[] r4 = TileBufferPool.borrow(4);
        int[] r5 = TileBufferPool.borrow(4);

        assertSame(fourth, r1, "most recently released array must be handed out first");
        assertSame(third, r2);
        assertSame(second, r3);
        assertSame(first, r4);
        assertNotSame(fifth, r5, "the 5th release exceeded the 4-array cap and must have been dropped");
        assertNotSame(first, r5);
        assertNotSame(second, r5);
        assertNotSame(third, r5);
        assertNotSame(fourth, r5);
    }
}
