package com.parallelimage.core.fork;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link ForkJoinConfig} tests. */
class ForkJoinConfigTest {

    @Nested
    @DisplayName("defaultParallelism")
    class DefaultParallelism {

        @Test
        @DisplayName("is at least 1, even on a single-core machine")
        void isAtLeastOne() {
            assertTrue(ForkJoinConfig.defaultParallelism() >= 1);
        }

        @Test
        @DisplayName("reserves one core from availableProcessors when more than one is available")
        void reservesOneCoreWhenPossible() {
            int cores = Runtime.getRuntime().availableProcessors();
            int expected = Math.max(1, cores - 1);
            assertEquals(expected, ForkJoinConfig.defaultParallelism());
        }
    }

    @Nested
    @DisplayName("newPool")
    class NewPool {

        @Test
        @DisplayName("an explicit positive parallelism is honored exactly")
        void explicitParallelismIsHonored() {
            ForkJoinPool pool = ForkJoinConfig.newPool(3);
            try {
                assertEquals(3, pool.getParallelism());
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("a non-positive parallelism falls back to defaultParallelism()")
        void nonPositiveParallelismFallsBackToDefault() {
            ForkJoinPool pool = ForkJoinConfig.newPool(0);
            try {
                assertEquals(ForkJoinConfig.defaultParallelism(), pool.getParallelism());
            } finally {
                pool.shutdownNow();
            }

            ForkJoinPool negative = ForkJoinConfig.newPool(-5);
            try {
                assertEquals(ForkJoinConfig.defaultParallelism(), negative.getParallelism());
            } finally {
                negative.shutdownNow();
            }
        }

        @Test
        @DisplayName("the no-arg overload behaves like newPool(0)")
        void noArgOverloadUsesDefault() {
            ForkJoinPool pool = ForkJoinConfig.newPool();
            try {
                assertEquals(ForkJoinConfig.defaultParallelism(), pool.getParallelism());
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("worker threads are daemon threads named with the pip-worker- prefix")
        void workerThreadsAreNamedDaemonThreads() throws InterruptedException {
            ForkJoinPool pool = ForkJoinConfig.newPool(1);
            try {
                String[] name = new String[1];
                boolean[] daemon = new boolean[1];
                pool.submit(() -> {
                    Thread current = Thread.currentThread();
                    name[0] = current.getName();
                    daemon[0] = current.isDaemon();
                }).join();

                assertTrue(name[0].startsWith("pip-worker-"), "unexpected worker thread name: " + name[0]);
                assertTrue(daemon[0], "fork/join workers must be daemon threads");
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("two pools created in succession get distinct worker name prefixes (pool id increments)")
        void successivePoolsGetDistinctIds() {
            ForkJoinPool first = ForkJoinConfig.newPool(1);
            ForkJoinPool second = ForkJoinConfig.newPool(1);
            try {
                String firstName = first.submit(() -> Thread.currentThread().getName()).join();
                String secondName = second.submit(() -> Thread.currentThread().getName()).join();
                assertNotEquals(firstName, secondName);
            } finally {
                first.shutdownNow();
                second.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("shutdownGracefully")
    class ShutdownGracefully {

        @Test
        @DisplayName("a null pool is treated as already shut down")
        void nullPoolReturnsTrue() {
            assertTrue(ForkJoinConfig.shutdownGracefully(null, 1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("an idle pool drains within the timeout")
        void idlePoolDrainsWithinTimeout() {
            ForkJoinPool pool = ForkJoinConfig.newPool(1);
            assertTrue(ForkJoinConfig.shutdownGracefully(pool, 5, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("a pool with a long-running task that outlives the timeout is forced down and reports false")
        void slowTaskForcesShutdownAndReportsFalse() {
            ForkJoinPool pool = ForkJoinConfig.newPool(1);
            pool.submit(() -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            boolean drained = ForkJoinConfig.shutdownGracefully(pool, 50, TimeUnit.MILLISECONDS);

            assertEquals(false, drained);
            assertTrue(pool.isShutdown());
        }
    }

    @Nested
    @DisplayName("isShenandoahActive / describe")
    class Diagnostics {

        @Test
        @DisplayName("isShenandoahActive() runs without throwing on any collector")
        void isShenandoahActiveDoesNotThrow() {
            ForkJoinConfig.isShenandoahActive();
        }

        @Test
        @DisplayName("describe() includes the pool's parallelism and a gc label")
        void describeIncludesParallelismAndGc() {
            ForkJoinPool pool = ForkJoinConfig.newPool(2);
            try {
                String summary = ForkJoinConfig.describe(pool);
                assertTrue(summary.contains("parallelism=2"), summary);
                assertTrue(summary.contains("gc="), summary);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
