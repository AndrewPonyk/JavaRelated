package com.example.concurrency.barber;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.concurrent.locks.ReentrantLock;

/** Multiple sleeping barbers serving customers from a semaphore-bounded waiting room. */
public final class SleepingBarberShop implements AutoCloseable {
    private final Semaphore waitingChairs;
    private final Semaphore customersAvailable = new Semaphore(0, true);
    private final ConcurrentLinkedQueue<Customer> customers = new ConcurrentLinkedQueue<>();
    private final ExecutorService workers;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicInteger servedCustomers = new AtomicInteger();
    private final AtomicInteger rejectedCustomers = new AtomicInteger();
    private final ReentrantLock admissionLock = new ReentrantLock(true);
    private final int barberCount;

    public SleepingBarberShop(int barberCount, int waitingChairCount, IntConsumer haircut) {
        if (barberCount < 1 || waitingChairCount < 1) {
            throw new IllegalArgumentException("barbers and waiting chairs must be positive");
        }
        Objects.requireNonNull(haircut, "haircut");
        this.barberCount = barberCount;
        waitingChairs = new Semaphore(waitingChairCount, true);
        AtomicInteger number = new AtomicInteger();
        workers = Executors.newFixedThreadPool(barberCount, runnable -> {
            Thread thread = new Thread(runnable, "barber-" + number.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        });
        for (int index = 0; index < barberCount; index++) {
            workers.submit(() -> work(haircut));
        }
    }

    /** Returns empty immediately when the shop is closed or no waiting chair is available. */
    public Optional<CompletableFuture<Integer>> requestHaircut(int customerId) {
        if (customerId < 0) {
            throw new IllegalArgumentException("customer id must be non-negative");
        }
        admissionLock.lock();
        try {
            if (!open.get() || !waitingChairs.tryAcquire()) {
                rejectedCustomers.incrementAndGet();
                return Optional.empty();
            }
            CompletableFuture<Integer> completion = new CompletableFuture<>();
            customers.add(new Customer(customerId, completion));
            customersAvailable.release();
            return Optional.of(completion);
        } finally {
            admissionLock.unlock();
        }
    }

    public int servedCustomers() {
        return servedCustomers.get();
    }

    public int rejectedCustomers() {
        return rejectedCustomers.get();
    }

    public int waitingCustomers() {
        return customers.size();
    }

    private void work(IntConsumer haircut) {
        while (open.get() || !customers.isEmpty()) {
            try {
                customersAvailable.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            Customer customer = customers.poll();
            if (customer == null) {
                continue;
            }
            waitingChairs.release();
            try {
                haircut.accept(customer.id());
                servedCustomers.incrementAndGet();
                customer.completion().complete(customer.id());
            } catch (RuntimeException failure) {
                customer.completion().completeExceptionally(failure);
            }
        }
    }

    @Override
    public void close() {
        admissionLock.lock();
        try {
            if (!open.compareAndSet(true, false)) {
                return;
            }
        } finally {
            admissionLock.unlock();
        }
        customersAvailable.release(barberCount);
        workers.shutdown();
        try {
            if (!workers.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
                workers.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            cancelWaitingCustomers();
        }
    }

    private void cancelWaitingCustomers() {
        Customer customer;
        while ((customer = customers.poll()) != null) {
            waitingChairs.release();
            customer.completion().completeExceptionally(
                    new CancellationException("barber shop closed before service"));
        }
    }

    private record Customer(int id, CompletableFuture<Integer> completion) {
    }
}
