package com.nearmeet.sim;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free: read the current value, then compareAndSet(current, current + 1).
 * If another thread changed it in between, CAS fails and we read again.
 * Same idea as the SQL "UPDATE ... WHERE seats_taken < capacity" used later in the app.
 */
public class AtomicSeatCounter implements SeatCounter {

    private final int capacity;
    private final AtomicInteger taken = new AtomicInteger();

    public AtomicSeatCounter(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean tryTake() {
        while (true) {
            int current = taken.get();
            if (current >= capacity) {
                return false;
            }
            if (taken.compareAndSet(current, current + 1)) {
                return true;
            }
            // lost the race for this value, try again with the fresh one
        }
    }

    @Override
    public int taken() {
        return taken.get();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public String name() {
        return "C: AtomicInteger CAS";
    }
}
