package com.example.concurrency.barber;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Standalone multiple-barber and bounded-waiting-room demonstration. */
public final class SleepingBarberShopDemo {
    private SleepingBarberShopDemo() {
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch barbersBusy = new CountDownLatch(2);
        CountDownLatch finishHaircuts = new CountDownLatch(1);
        List<CompletableFuture<Integer>> accepted = new ArrayList<>();

        try (SleepingBarberShop shop = new SleepingBarberShop(2, 3, customerId -> {
            barbersBusy.countDown();
            try {
                finishHaircuts.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        })) {
            accepted.add(shop.requestHaircut(1).orElseThrow());
            accepted.add(shop.requestHaircut(2).orElseThrow());
            if (!barbersBusy.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("barbers did not start serving");
            }

            accepted.add(shop.requestHaircut(3).orElseThrow());
            accepted.add(shop.requestHaircut(4).orElseThrow());
            accepted.add(shop.requestHaircut(5).orElseThrow());
            Optional<CompletableFuture<Integer>> rejected = shop.requestHaircut(6);
            finishHaircuts.countDown();

            for (CompletableFuture<Integer> completion : accepted) {
                completion.get(2, TimeUnit.SECONDS);
            }
            System.out.printf(
                    "Sleeping Barber: barbers=2, waitingChairs=3, served=%d, rejected=%d, fullRoomRejected=%s%n",
                    shop.servedCustomers(),
                    shop.rejectedCustomers(),
                    rejected.isEmpty());
        } finally {
            finishHaircuts.countDown();
        }
    }
}
