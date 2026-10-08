package com.darshan.circl.participation;

public enum WaitlistStatus {
    WAITING, OFFERED, CLAIMED, EXPIRED, DECLINED;

    public boolean isActive() {
        return this == WAITING || this == OFFERED;
    }
}
