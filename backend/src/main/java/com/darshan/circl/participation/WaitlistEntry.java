package com.darshan.circl.participation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;

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

    @Generated
    @Column(insertable = false, updatable = false)
    private Long position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(UUID activityId, UUID userId, Instant createdAt) {
        this.activityId = activityId;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public UUID getUserId() { return userId; }
    public Long getPosition() { return position; }
    public Instant getCreatedAt() { return createdAt; }
}
