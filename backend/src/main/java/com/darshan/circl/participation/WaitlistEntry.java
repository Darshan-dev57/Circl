package com.darshan.circl.participation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waitlist")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "activity_id", nullable = false, updatable = false)
    private UUID activityId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "party_size", nullable = false, updatable = false)
    private int partySize = 1;

    @Generated
    @Column(insertable = false, updatable = false)
    private Long position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "claim_deadline")
    private Instant claimDeadline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(UUID activityId, UUID userId, int partySize, Instant createdAt) {
        this.activityId = activityId;
        this.userId = userId;
        this.partySize = partySize;
        this.createdAt = createdAt;
    }

    public void offer(Instant now, Duration claimWindow) {
        status = WaitlistStatus.OFFERED;
        offeredAt = now;
        claimDeadline = now.plus(claimWindow);
    }

    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public UUID getUserId() { return userId; }
    public int getPartySize() { return partySize; }
    public Long getPosition() { return position; }
    public WaitlistStatus getStatus() { return status; }
    public Instant getOfferedAt() { return offeredAt; }
    public Instant getClaimDeadline() { return claimDeadline; }
    public Instant getCreatedAt() { return createdAt; }
}
