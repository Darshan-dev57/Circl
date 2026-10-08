package com.darshan.circl.common.web;

import com.darshan.circl.common.error.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Opaque keyset cursor: base64 of "startsAt|id" of the last row the client saw. */
public record Cursor(Instant startsAt, UUID id) {

    public String encode() {
        String raw = startsAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad-cursor", "cursor is not valid");
        }
    }
}
