package com.darshan.circl.sim;

/**
 * Same check-then-act, but the whole method holds the object's monitor.
 * Only one thread at a time can check and take, so no overbooking.
 * Works only inside ONE JVM: two app servers have two different locks.
 */
public class SynchronizedSeatCounter implements SeatCounter {

    private final int capacity;
    private int taken = 0;

    public SynchronizedSeatCounter(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized boolean tryTake() {
        if (taken < capacity) {
            taken = taken + 1;
            return true;
        }
        return false;
    }

    @Override
    public synchronized int taken() {
        return taken;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public String name() {
        return "B: synchronized";
    }
}
