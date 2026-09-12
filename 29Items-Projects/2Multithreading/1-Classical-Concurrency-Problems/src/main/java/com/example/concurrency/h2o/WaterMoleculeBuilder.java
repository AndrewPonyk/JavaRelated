package com.example.concurrency.h2o;

import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/** Forms barrier generations containing exactly two hydrogen atoms and one oxygen atom. */
public final class WaterMoleculeBuilder {
    private final Semaphore hydrogenSlots = new Semaphore(2, true);
    private final Semaphore oxygenSlots = new Semaphore(1, true);
    private final AtomicLong molecules = new AtomicLong();
    private final CyclicBarrier moleculeBarrier = new CyclicBarrier(3, molecules::incrementAndGet);

    public void hydrogen(Runnable bondAction) throws InterruptedException {
        bond(hydrogenSlots, bondAction);
    }

    public void oxygen(Runnable bondAction) throws InterruptedException {
        bond(oxygenSlots, bondAction);
    }

    public long moleculeCount() {
        return molecules.get();
    }

    private void bond(Semaphore admission, Runnable bondAction) throws InterruptedException {
        Objects.requireNonNull(bondAction, "bondAction");
        admission.acquire();
        try {
            try {
                bondAction.run();
            } catch (RuntimeException failure) {
                moleculeBarrier.reset();
                throw failure;
            }
            awaitMolecule();
        } finally {
            admission.release();
        }
    }

    private void awaitMolecule() throws InterruptedException {
        try {
            moleculeBarrier.await();
        } catch (BrokenBarrierException brokenBarrier) {
            throw new IllegalStateException("molecule barrier was broken", brokenBarrier);
        }
    }
}
