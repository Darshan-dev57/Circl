package com.darshan.circl.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "no_show_appeals")
public class NoShowAppeal {

    public enum Status { OPEN, ACCEPTED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    @Column(name = "user_reason", nullable = false, length = 500)
    private String userReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(name = "appeal_closes_at", nullable = false)
    private Instant appealClosesAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NoShowAppeal() {
    }

    public NoShowAppeal(UUID participantId, String userReason, Instant appealClosesAt, Instant createdAt) {
        this.participantId = participantId;
        this.userReason = userReason;
        this.appealClosesAt = appealClosesAt;
        this.createdAt = createdAt;
    }

    public void decide(boolean accept, String note, Instant when) {
        status = accept ? Status.ACCEPTED : Status.REJECTED;
        decisionNote = note;
        decidedAt = when;
    }

    public UUID getId() { return id; }
    public UUID getParticipantId() { return participantId; }
    public String getUserReason() { return userReason; }
    public Status getStatus() { return status; }
    public Instant getAppealClosesAt() { return appealClosesAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
