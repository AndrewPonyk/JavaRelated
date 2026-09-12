package com.example.concurrency.bathroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.concurrency.bathroom.UnisexBathroom.Group;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class UnisexBathroomTest {

    @Test
    void validatesCapacityGroupAndLeaveState() {
        assertThrows(IllegalArgumentException.class, () -> new UnisexBathroom(0));
        UnisexBathroom bathroom = new UnisexBathroom(1);
        assertThrows(NullPointerException.class, () -> bathroom.enter(null));
        assertThrows(NullPointerException.class, () -> bathroom.leave(null));
        assertThrows(IllegalStateException.class, () -> bathroom.leave(Group.A));
    }

    @Test
    @Timeout(10)
    void enforcesCapacityAndBlocksTheOppositeGroup() throws Exception {
        UnisexBathroom bathroom = new UnisexBathroom(2);
        bathroom.enter(Group.A);
        bathroom.enter(Group.A);
        assertEquals(2, bathroom.snapshot().occupancy());

        CountDownLatch groupBAttempted = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> groupB = executor.submit(() -> {
                groupBAttempted.countDown();
                bathroom.enter(Group.B);
                bathroom.leave(Group.B);
                return true;
            });
            assertTrue(groupBAttempted.await(1, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> groupB.get(100, TimeUnit.MILLISECONDS));

            bathroom.leave(Group.A);
            bathroom.leave(Group.A);
            assertTrue(groupB.get(2, TimeUnit.SECONDS));
        }

        assertEquals(0, bathroom.snapshot().occupancy());
        assertNull(bathroom.snapshot().activeGroup());
    }
}
