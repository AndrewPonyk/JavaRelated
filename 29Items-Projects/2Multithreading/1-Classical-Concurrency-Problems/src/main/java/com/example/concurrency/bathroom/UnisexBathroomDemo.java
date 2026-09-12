package com.example.concurrency.bathroom;

import com.example.concurrency.bathroom.UnisexBathroom.Group;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone fair, capacity-limited unisex-bathroom demonstration. */
public final class UnisexBathroomDemo {
    private UnisexBathroomDemo() {
    }

    public static void main(String[] args) throws Exception {
        UnisexBathroom bathroom = new UnisexBathroom(2);
        Object observationLock = new Object();
        AtomicReference<Group> observedGroup = new AtomicReference<>();
        AtomicInteger observedOccupancy = new AtomicInteger();
        AtomicInteger maximumOccupancy = new AtomicInteger();
        AtomicBoolean mixedGroups = new AtomicBoolean();

        try (ExecutorService executor = Executors.newFixedThreadPool(6)) {
            List<Future<?>> visits = new ArrayList<>();
            for (int visitor = 0; visitor < 6; visitor++) {
                Group group = visitor % 2 == 0 ? Group.A : Group.B;
                visits.add(executor.submit(() -> visit(
                        bathroom,
                        group,
                        observationLock,
                        observedGroup,
                        observedOccupancy,
                        maximumOccupancy,
                        mixedGroups)));
            }
            for (Future<?> visit : visits) {
                visit.get(3, TimeUnit.SECONDS);
            }
        }

        System.out.printf(
                "Unisex Bathroom: visits=6, capacity=2, maxOccupancy=%d, mixedGroups=%s%n",
                maximumOccupancy.get(),
                mixedGroups.get());
    }

    private static Void visit(
            UnisexBathroom bathroom,
            Group group,
            Object observationLock,
            AtomicReference<Group> observedGroup,
            AtomicInteger observedOccupancy,
            AtomicInteger maximumOccupancy,
            AtomicBoolean mixedGroups) throws InterruptedException {
        bathroom.enter(group);
        try {
            synchronized (observationLock) {
                Group current = observedGroup.get();
                if (current != null && current != group) {
                    mixedGroups.set(true);
                }
                observedGroup.set(group);
                int occupancy = observedOccupancy.incrementAndGet();
                maximumOccupancy.accumulateAndGet(occupancy, Math::max);
            }
            TimeUnit.MILLISECONDS.sleep(20);
        } finally {
            synchronized (observationLock) {
                if (observedOccupancy.decrementAndGet() == 0) {
                    observedGroup.set(null);
                }
            }
            bathroom.leave(group);
        }
        return null;
    }
}
