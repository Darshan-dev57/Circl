package com.darshan.circl.attendance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Short-lived check-in code the host shows as a QR: activityId|expiresAt|nonce, signed with HMAC-SHA256.
 * A screenshot sent to a friend stops working within a minute, and the friend still has to be a participant.
 */
@Component
public class CheckinTokens {

    public record Issued(String token, Instant expiresAt) {
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CheckinTokens(@Value("${circl.checkin.secret}") String secret,
                         @Value("${circl.checkin.token-ttl:PT60S}") Duration ttl,
                         Clock clock) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("circl.checkin.secret (CIRCL_CHECKIN_SECRET) must be at least 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    public Issued issue(UUID activityId) {
        Instant expiresAt = Instant.now(clock).plus(ttl);
        byte[] nonce = new byte[8];
        random.nextBytes(nonce);
        String payload = activityId + "|" + expiresAt.getEpochSecond() + "|" + HexFormat.of().formatHex(nonce);
        String token = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + B64.encodeToString(sign(payload));
        return new Issued(token, expiresAt);
    }

    public boolean isValid(String token, UUID activityId) {
        if (token == null) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        try {
            String payload = new String(B64D.decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] sig = B64D.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(payload), sig)) {
                return false;
            }
            String[] parts = payload.split("\\|");
            return parts.length == 3
                    && parts[0].equals(activityId.toString())
                    && Instant.ofEpochSecond(Long.parseLong(parts[1])).isAfter(Instant.now(clock));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
