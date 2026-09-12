package com.example.concurrency.deadlock;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;

/** Read-only JVM deadlock diagnostics using owned-monitor and synchronizer information. */
public final class DeadlockDetector {
    private final ThreadMXBean threadBean;

    public DeadlockDetector() {
        this(ManagementFactory.getThreadMXBean());
    }

    DeadlockDetector(ThreadMXBean threadBean) {
        this.threadBean = threadBean;
    }

    public List<DeadlockedThread> detect() {
        long[] ids = threadBean.findDeadlockedThreads();
        if (ids == null) {
            return List.of();
        }
        ThreadInfo[] infos = threadBean.getThreadInfo(ids, true, true);
        return Arrays.stream(infos)
                .filter(info -> info != null)
                .map(info -> new DeadlockedThread(
                        info.getThreadId(),
                        info.getThreadName(),
                        info.getThreadState(),
                        info.getLockName(),
                        info.getLockOwnerName()))
                .toList();
    }

    public record DeadlockedThread(
            long id,
            String name,
            Thread.State state,
            String lockName,
            String lockOwnerName) {
    }
}
