package com.darshan.circl.participation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.darshan.circl.attendance.AttendanceStatus;
import com.darshan.circl.attendance.IllegalTransitionException;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "participants")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private UUID activityId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "party_size", nullable = false)
    private int partySize = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipantStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 16)
    private AttendanceStatus attendanceStatus = AttendanceStatus.RSVP;

    @Column(nullable = false)
    private boolean late;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Column(name = "host_evidence", length = 500)
    private String hostEvidence;

    @Column(name = "score_applied", nullable = false)
    private boolean scoreApplied;

    @Version
    private long version;

    protected Participant() {
    }

    public Participant(UUID activityId, UUID userId, int partySize, Instant joinedAt) {
        this.activityId = activityId;
        this.userId = userId;
        this.partySize = partySize;
        this.status = ParticipantStatus.JOINED;
        this.joinedAt = joinedAt;
    }

    public boolean isJoined() {
        return status == ParticipantStatus.JOINED;
    }

    public void rejoin(int partySize, Instant when) {
        this.partySize = partySize;
        status = ParticipantStatus.JOINED;
        joinedAt = when;
        leftAt = null;
        if (attendanceStatus != AttendanceStatus.RSVP) {
            moveTo(AttendanceStatus.RSVP);
        }
        late = false;
        checkedInAt = null;
    }

    public void leave(Instant when) {
        status = ParticipantStatus.LEFT;
        leftAt = when;
        moveTo(AttendanceStatus.CANCELLED);
    }

    /** the only way attendance changes; returns the previous status */
    public AttendanceStatus moveTo(AttendanceStatus next) {
        AttendanceStatus previous = attendanceStatus;
        if (!previous.canMoveTo(next)) {
            throw new IllegalTransitionException(previous, next);
        }
        attendanceStatus = next;
        return previous;
    }

    public void markCheckedIn(Instant when, boolean late) {
        moveTo(AttendanceStatus.CHECKED_IN);
        this.checkedInAt = when;
        this.late = late;
    }

    public void markNoShow(Instant when) {
        moveTo(AttendanceStatus.NO_SHOW);
        this.noShowAt = when;
    }

    public void addHostEvidence(String note) {
        this.hostEvidence = note;
    }

    public void markScoreApplied() {
        this.scoreApplied = true;
    }

    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public UUID getUserId() { return userId; }
    public int getPartySize() { return partySize; }
    public AttendanceStatus getAttendanceStatus() { return attendanceStatus; }
    public boolean isLate() { return late; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getNoShowAt() { return noShowAt; }
    public String getHostEvidence() { return hostEvidence; }
    public boolean isScoreApplied() { return scoreApplied; }
    public ParticipantStatus getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }
}
