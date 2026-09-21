package com.darshan.circl.attendance;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum AttendanceStatus {
    RSVP, RECONFIRMED, CHECKED_IN, ATTENDED, CANCELLED, NO_SHOW;

    private static final Map<AttendanceStatus, Set<AttendanceStatus>> ALLOWED = Map.of(
            RSVP, EnumSet.of(RECONFIRMED, CHECKED_IN, CANCELLED, NO_SHOW),
            RECONFIRMED, EnumSet.of(CHECKED_IN, CANCELLED, NO_SHOW),
            CHECKED_IN, EnumSet.of(ATTENDED),
            // host override when the scan failed (dead phone, bad network)
            NO_SHOW, EnumSet.of(ATTENDED),
            // rejoining after a cancel starts over
            CANCELLED, EnumSet.of(RSVP),
            ATTENDED, EnumSet.noneOf(AttendanceStatus.class));

    public boolean canMoveTo(AttendanceStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
