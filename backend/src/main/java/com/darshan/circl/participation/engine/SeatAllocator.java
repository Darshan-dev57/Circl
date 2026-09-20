package com.darshan.circl.participation.engine;

import java.util.UUID;

public interface SeatAllocator {

    JoinStrategy strategy();

    /** must run inside a transaction; true = the seats are ours */
    boolean tryTakeSeats(UUID activityId, int seats);
}
