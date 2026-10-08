package com.nearmeet.sim;

/**
 * Something that hands out a fixed number of seats.
 * tryTake() returns true if the caller got a seat.
 */
public interface SeatCounter {

    boolean tryTake();

    /** seats handed out so far (can be more than capacity if the counter is broken) */
    int taken();

    int capacity();

    String name();
}
