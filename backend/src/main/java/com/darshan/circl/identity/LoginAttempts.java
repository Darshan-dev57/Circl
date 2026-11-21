package com.darshan.circl.identity;

import com.darshan.circl.common.error.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Counts failed logins per email in Redis (fixed window). After too many the email is
 * blocked until the window ends. If Redis is down login fails closed rather than allowing
 * unlimited guessing.
 */
@Component
public class LoginAttempts {

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final Duration window;

    public LoginAttempts(StringRedisTemplate redis,
                         @Value("${circl.login.max-failures:5}") int maxFailures,
                         @Value("${circl.login.window:PT15M}") Duration window) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    private static String key(String email, String remoteIp) {
        return "login:fail:" + digest(email) + ":" + digest(remoteIp == null ? "unknown" : remoteIp);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public void checkAllowed(String email, String remoteIp) {
        String key = key(email, remoteIp);
        String value = redis.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= maxFailures) {
            Long ttl = redis.getExpire(key);
            throw new TooManyRequestsException("Too many failed logins, try again later",
                    ttl == null || ttl < 0 ? window.toSeconds() : ttl);
        }
    }

    public void recordFailure(String email, String remoteIp) {
        String key = key(email, remoteIp);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
    }

    public void reset(String email, String remoteIp) {
        redis.delete(key(email, remoteIp));
    }
}
