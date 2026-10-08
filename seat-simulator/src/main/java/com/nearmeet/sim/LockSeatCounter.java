package com.nearmeet.sim;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Explicit lock instead of synchronized. Same guarantee, but it can be tried
 * with a timeout and it does not pin a virtual thread to its carrier.
 */
public class LockSeatCounter implements SeatCounter {

    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private int taken = 0;

    public LockSeatCounter(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean tryTake() {
        lock.lock();
        try {
            if (taken < capacity) {
                taken = taken + 1;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int taken() {
        lock.lock();
        try {
            return taken;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public String name() {
        return "D: ReentrantLock";
    }
}
