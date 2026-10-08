package com.darshan.circl.participation.engine;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.common.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Throws ObjectOptimisticLockingFailureException on a lost race; the caller retries the transaction. */
@Component
class OptimisticAllocator implements SeatAllocator {

    private final ActivityRepository activities;

    OptimisticAllocator(ActivityRepository activities) {
        this.activities = activities;
    }

    @Override
    public JoinStrategy strategy() {
        return JoinStrategy.OPTIMISTIC;
    }

    @Override
    public boolean tryTakeSeats(UUID activityId, int seats) {
        Activity activity = activities.findById(activityId)
                .orElseThrow(() -> new NotFoundException("Activity", activityId));
        if (!activity.isOpen() || activity.seatsLeft() < seats) {
            return false;
        }
        activity.setSeatsTaken(activity.getSeatsTaken() + seats);
        activities.saveAndFlush(activity);
        return true;
    }
}
