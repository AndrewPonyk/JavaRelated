package com.example.concurrency.barber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SleepingBarberShopTest {

    @Test
    void validatesConfigurationInputAndClosedAdmission() {
        assertThrows(IllegalArgumentException.class, () -> new SleepingBarberShop(0, 1, id -> { }));
        assertThrows(IllegalArgumentException.class, () -> new SleepingBarberShop(1, 0, id -> { }));
        assertThrows(NullPointerException.class, () -> new SleepingBarberShop(1, 1, null));

        SleepingBarberShop shop = new SleepingBarberShop(1, 1, id -> { });
        assertThrows(IllegalArgumentException.class, () -> shop.requestHaircut(-1));
        shop.close();
        shop.close();
        assertTrue(shop.requestHaircut(1).isEmpty());
        assertEquals(1, shop.rejectedCustomers());
    }

    @Test
    @Timeout(5)
    void reportsHaircutCallbackFailuresToTheCustomer() throws Exception {
        try (SleepingBarberShop shop = new SleepingBarberShop(1, 1, id -> {
            throw new IllegalStateException("clippers failed");
        })) {
            CompletableFuture<Integer> completion = shop.requestHaircut(1).orElseThrow();
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> completion.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertEquals(0, shop.servedCustomers());
        }
    }

    @Test
    @Timeout(10)
    void boundsWaitingRoomAndServesAcceptedCustomers() throws Exception {
        CountDownLatch firstHaircutsStarted = new CountDownLatch(2);
        CountDownLatch finishHaircuts = new CountDownLatch(1);

        try (SleepingBarberShop shop = new SleepingBarberShop(2, 2, customerId -> {
            firstHaircutsStarted.countDown();
            try {
                finishHaircuts.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        })) {
            List<CompletableFuture<Integer>> accepted = new ArrayList<>();
            accepted.add(shop.requestHaircut(1).orElseThrow());
            accepted.add(shop.requestHaircut(2).orElseThrow());
            assertTrue(firstHaircutsStarted.await(2, TimeUnit.SECONDS));

            accepted.add(shop.requestHaircut(3).orElseThrow());
            accepted.add(shop.requestHaircut(4).orElseThrow());
            Optional<CompletableFuture<Integer>> rejected = shop.requestHaircut(5);
            assertTrue(rejected.isEmpty());

            finishHaircuts.countDown();
            for (CompletableFuture<Integer> completion : accepted) {
                completion.get(2, TimeUnit.SECONDS);
            }
            assertEquals(4, shop.servedCustomers());
            assertEquals(1, shop.rejectedCustomers());
        } finally {
            finishHaircuts.countDown();
        }
    }
}
