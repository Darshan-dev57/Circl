package com.darshan.circl.participation.engine;

import com.darshan.circl.activity.ActivityRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ConditionalUpdateAllocator implements SeatAllocator {

    private final ActivityRepository activities;

    ConditionalUpdateAllocator(ActivityRepository activities) {
        this.activities = activities;
    }

    @Override
    public JoinStrategy strategy() {
        return JoinStrategy.CONDITIONAL_UPDATE;
    }

    @Override
    public boolean tryTakeSeats(UUID activityId, int seats) {
        return activities.tryTakeSeats(activityId, seats) == 1;
    }
}
