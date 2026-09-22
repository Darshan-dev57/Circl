package com.darshan.circl.activity.live;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
public class SeatStreamController {

    private final SeatStream seats;

    public SeatStreamController(SeatStream seats) {
        this.seats = seats;
    }

    // public like GET /activities/{id}; EventSource in the browser cannot send an Authorization header anyway
    @GetMapping(path = "/api/v1/activities/{activityId}/seats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter seats(@PathVariable UUID activityId) {
        return seats.subscribe(activityId);
    }
}
