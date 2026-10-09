package com.darshan.circl.activity.live;

import com.darshan.circl.common.outbox.ActivityEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Second consumer group on the same topic: it gets its own copy of every event, so live seat counts
 * keep working even if notifications fall behind. A duplicate only re-sends the current count.
 */
@Component
public class SeatEventListener {

    private final SeatStream seats;

    public SeatEventListener(SeatStream seats) {
        this.seats = seats;
    }

    @KafkaListener(topics = "${circl.events.topic}", groupId = "circl-live-seats")
    public void on(ActivityEvent event) {
        if (SeatStream.SEAT_EVENTS.contains(event.type())) {
            seats.publish(List.of(event.activityId()));
        }
    }
}
