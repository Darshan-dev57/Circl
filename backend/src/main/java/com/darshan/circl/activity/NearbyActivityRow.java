package com.darshan.circl.activity;

import java.time.Instant;
import java.util.UUID;

public interface NearbyActivityRow {
    UUID getId();
    String getTitle();
    String getCategory();
    Instant getStartsAt();
    double getLat();
    double getLng();
    int getCapacity();
    int getSeatsTaken();
    double getDistanceM();
}
