package com.darshan.circl.activity.live;

import com.darshan.circl.activity.ActivityStatus;

import java.util.UUID;

public record SeatUpdate(UUID activityId, int capacity, int seatsTaken, int seatsLeft, ActivityStatus status) {
}
