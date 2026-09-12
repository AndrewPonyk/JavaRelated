package com.example.concurrency.bathroom;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Capacity-limited resource with category exclusivity and starvation-resistant turn taking. */
public final class UnisexBathroom {
    public enum Group {
        A,
        B;

        public Group opposite() {
            return this == A ? B : A;
        }
    }

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition groupAChanged = lock.newCondition();
    private final Condition groupBChanged = lock.newCondition();
    private final int capacity;
    private Group activeGroup;
    private Group turn = Group.A;
    private int occupancy;
    private int waitingA;
    private int waitingB;

    public UnisexBathroom(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public void enter(Group group) throws InterruptedException {
        Objects.requireNonNull(group, "group");
        lock.lockInterruptibly();
        boolean admitted = false;
        incrementWaiting(group);
        try {
            while (!canEnter(group)) {
                condition(group).await();
            }
            decrementWaiting(group);
            admitted = true;
            activeGroup = group;
            occupancy++;
            signalEligible();
        } finally {
            if (!admitted) {
                decrementWaiting(group);
                signalEligible();
            }
            lock.unlock();
        }
    }

    public void leave(Group group) {
        Objects.requireNonNull(group, "group");
        lock.lock();
        try {
            if (activeGroup != group || occupancy == 0) {
                throw new IllegalStateException("group is not currently occupying the bathroom");
            }
            occupancy--;
            if (occupancy == 0) {
                activeGroup = null;
                if (waiting(group.opposite()) > 0) {
                    turn = group.opposite();
                } else {
                    turn = group;
                }
            }
            signalEligible();
        } finally {
            lock.unlock();
        }
    }

    public Snapshot snapshot() {
        lock.lock();
        try {
            return new Snapshot(activeGroup, occupancy, waitingA, waitingB, turn, capacity);
        } finally {
            lock.unlock();
        }
    }

    private boolean canEnter(Group group) {
        if (occupancy >= capacity) {
            return false;
        }
        if (activeGroup == group) {
            return waiting(group.opposite()) == 0;
        }
        if (activeGroup != null) {
            return false;
        }
        return waiting(group.opposite()) == 0 || turn == group;
    }

    private void signalEligible() {
        if (activeGroup == null) {
            Group preferred = waiting(turn) > 0 ? turn : turn.opposite();
            condition(preferred).signalAll();
        } else if (waiting(activeGroup.opposite()) == 0 && occupancy < capacity) {
            condition(activeGroup).signalAll();
        }
    }

    private Condition condition(Group group) {
        return group == Group.A ? groupAChanged : groupBChanged;
    }

    private int waiting(Group group) {
        return group == Group.A ? waitingA : waitingB;
    }

    private void incrementWaiting(Group group) {
        if (group == Group.A) {
            waitingA++;
        } else {
            waitingB++;
        }
    }

    private void decrementWaiting(Group group) {
        if (group == Group.A) {
            waitingA--;
        } else {
            waitingB--;
        }
    }

    public record Snapshot(
            Group activeGroup,
            int occupancy,
            int waitingA,
            int waitingB,
            Group turn,
            int capacity) {
    }
}
