package com.darshan.circl.participation.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, IdempotencyId> {

    /**
     * Claims the key. If another request holds it, this blocks on the primary key
     * until that transaction ends and then inserts nothing (returns 0).
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (user_id, idem_key, request_hash, created_at, expires_at)
            VALUES (:userId, :key, :hash, :now, :expiresAt)
            ON CONFLICT (user_id, idem_key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(@Param("userId") UUID userId, @Param("key") String key, @Param("hash") String hash,
                 @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE expires_at < :now", nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
