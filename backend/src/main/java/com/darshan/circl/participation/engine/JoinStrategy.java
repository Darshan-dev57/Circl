package com.darshan.circl.participation.engine;

public enum JoinStrategy {
    /** UPDATE ... SET seats_taken = seats_taken + n WHERE seats_taken + n <= capacity */
    CONDITIONAL_UPDATE,
    /** SELECT ... FOR UPDATE on the activity row, then check and write */
    PESSIMISTIC,
    /** read with @Version, write, retry when someone else changed the row first */
    OPTIMISTIC
}
