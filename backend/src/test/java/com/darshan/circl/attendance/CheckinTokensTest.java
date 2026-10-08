package com.darshan.circl.attendance;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckinTokensTest {

    static final String SECRET = "test-secret-that-is-long-enough-123456";
    final UUID activity = UUID.randomUUID();

    @Test
    void validForItsActivityOnly() {
        CheckinTokens tokens = new CheckinTokens(SECRET, Duration.ofSeconds(60), Clock.systemUTC());
        String token = tokens.issue(activity).token();
        assertThat(tokens.isValid(token, activity)).isTrue();
        assertThat(tokens.isValid(token, UUID.randomUUID())).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        CheckinTokens tokens = new CheckinTokens(SECRET, Duration.ofSeconds(60), Clock.systemUTC());
        String token = tokens.issue(activity).token();
        String forged = token.substring(0, token.indexOf('.')) + ".AAAA" + token.substring(token.indexOf('.') + 5);
        assertThat(tokens.isValid(forged, activity)).isFalse();
        assertThat(tokens.isValid("garbage", activity)).isFalse();
        assertThat(tokens.isValid(null, activity)).isFalse();
    }

    @Test
    void expiresAfterTheTtl() {
        Instant t0 = Instant.parse("2026-10-10T12:00:00Z");
        String token = new CheckinTokens(SECRET, Duration.ofSeconds(60), Clock.fixed(t0, ZoneOffset.UTC)).issue(activity).token();
        CheckinTokens later = new CheckinTokens(SECRET, Duration.ofSeconds(60), Clock.fixed(t0.plusSeconds(61), ZoneOffset.UTC));
        assertThat(later.isValid(token, activity)).isFalse();
    }

    @Test
    void signedWithADifferentSecretIsRejected() {
        String token = new CheckinTokens(SECRET, Duration.ofSeconds(60), Clock.systemUTC()).issue(activity).token();
        CheckinTokens other = new CheckinTokens("another-secret-that-is-long-enough-99", Duration.ofSeconds(60), Clock.systemUTC());
        assertThat(other.isValid(token, activity)).isFalse();
    }

    @Test
    void shortSecretRefusesToStart() {
        assertThatThrownBy(() -> new CheckinTokens("short", Duration.ofSeconds(60), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
