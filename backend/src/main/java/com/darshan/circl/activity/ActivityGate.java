package com.darshan.circl.activity;

import java.time.Instant;
import java.util.UUID;

/** The few columns needed to decide whether someone may try to join. */
public interface ActivityGate {
    UUID getHostId();
    ActivityStatus getStatus();
    Instant getStartsAt();
    int getMinReliability();
}
