package com.darshan.circl.participation.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @EmbeddedId
    private IdempotencyId id;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public boolean isCompleted() {
        return responseStatus != null;
    }

    public void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    public IdempotencyId getId() { return id; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getExpiresAt() { return expiresAt; }
}
