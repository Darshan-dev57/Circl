package com.darshan.circl.attendance;

import com.darshan.circl.participation.Participant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceStatusTest {

    @ParameterizedTest
    @CsvSource({
            "RSVP, RECONFIRMED", "RSVP, CHECKED_IN", "RSVP, CANCELLED", "RSVP, NO_SHOW",
            "RECONFIRMED, CHECKED_IN", "CHECKED_IN, ATTENDED", "NO_SHOW, ATTENDED", "CANCELLED, RSVP"
    })
    void legalMoves(AttendanceStatus from, AttendanceStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "ATTENDED, NO_SHOW", "ATTENDED, CANCELLED", "CHECKED_IN, NO_SHOW", "CHECKED_IN, CANCELLED",
            "NO_SHOW, CHECKED_IN", "CANCELLED, CHECKED_IN", "RECONFIRMED, RSVP"
    })
    void illegalMoves(AttendanceStatus from, AttendanceStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @Test
    void participantRefusesAnIllegalMove() {
        Participant p = new Participant(UUID.randomUUID(), UUID.randomUUID(), 1, Instant.now());
        p.markCheckedIn(Instant.now(), false);
        p.moveTo(AttendanceStatus.ATTENDED);

        assertThatThrownBy(() -> p.markNoShow(Instant.now())).isInstanceOf(IllegalTransitionException.class);
        assertThat(p.getAttendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
    }
}
