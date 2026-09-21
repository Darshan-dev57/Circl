package com.darshan.circl.attendance;

import com.darshan.circl.common.error.ConflictException;

public class IllegalTransitionException extends ConflictException {

    public IllegalTransitionException(AttendanceStatus from, AttendanceStatus to) {
        super("illegal-attendance-transition", "Cannot move attendance from " + from + " to " + to);
    }
}
