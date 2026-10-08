package com.darshan.circl.participation.engine;

import com.darshan.circl.activity.Activity;
import com.darshan.circl.activity.ActivityRepository;
import com.darshan.circl.common.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PessimisticAllocator implements SeatAllocator {

    private final ActivityRepository activities;

    PessimisticAllocator(ActivityRepository activities) {
        this.activities = activities;
    }

    @Override
    public JoinStrategy strategy() {
        return JoinStrategy.PESSIMISTIC;
    }

    @Override
    public boolean tryTakeSeats(UUID activityId, int seats) {
        Activity activity = activities.findByIdForUpdate(activityId)
                .orElseThrow(() -> new NotFoundException("Activity", activityId));
        if (!activity.isOpen() || activity.seatsLeft() < seats) {
            return false;
        }
        activity.setSeatsTaken(activity.getSeatsTaken() + seats);
        activities.flush();
        return true;
    }
}
