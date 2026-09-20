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
    }

    public void leave(Instant when) {
        status = ParticipantStatus.LEFT;
        leftAt = when;
    }

    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public UUID getUserId() { return userId; }
    public int getPartySize() { return partySize; }
    public ParticipantStatus getStatus() { return status; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getLeftAt() { return leftAt; }
}
