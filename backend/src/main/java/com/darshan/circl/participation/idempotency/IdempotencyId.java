package com.darshan.circl.participation.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record IdempotencyId(
        @Column(name = "user_id") UUID userId,
        @Column(name = "idem_key") String key
) implements Serializable {
}
