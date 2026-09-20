package com.nearmeet.sim;

/**
 * No locking at all. Check-then-act on a plain int:
 * two threads can both read taken = 9, both see a free seat, both write 10.
 * The sleep makes the window wider so the bug shows up on every run.
 */
public class UnsafeSeatCounter implements SeatCounter {

    private final int capacity;
    private int taken = 0;

    public UnsafeSeatCounter(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean tryTake() {
        if (taken < capacity) {
            pause();
            taken = taken + 1;
            return true;
        }
        return false;
    }

    private static void pause() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int taken() {
        return taken;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public String name() {
        return "A: no lock";
    }
}
