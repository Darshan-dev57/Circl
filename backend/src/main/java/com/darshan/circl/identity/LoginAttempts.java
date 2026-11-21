package com.darshan.circl.identity;

import com.darshan.circl.common.error.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Counts failed logins per account/IP pair and per IP in Redis (fixed windows). The pair
 * limit reduces remote account-lockout abuse; the IP limit also slows password spraying
 * across many accounts. If Redis is down login fails closed rather than allowing guessing.
 */
@Component
public class LoginAttempts {

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final int maxIpFailures;
    private final Duration window;

    public LoginAttempts(StringRedisTemplate redis,
                         @Value("${circl.login.max-failures:5}") int maxFailures,
                         @Value("${circl.login.ip-max-failures:30}") int maxIpFailures,
                         @Value("${circl.login.window:PT15M}") Duration window) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.maxIpFailures = maxIpFailures;
        this.window = window;
    }

    private static String key(String email, String remoteIp) {
        return "login:fail:" + digest(email) + ":" + digest(remoteIp == null ? "unknown" : remoteIp);
    }

    private static String ipKey(String remoteIp) {
        return "login:fail:ip:" + digest(remoteIp == null ? "unknown" : remoteIp);
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
        checkCounter(key, maxFailures);
        checkCounter(ipKey(remoteIp), maxIpFailures);
    }

    public void recordFailure(String email, String remoteIp) {
        increment(key(email, remoteIp));
        increment(ipKey(remoteIp));
    }

    private void checkCounter(String key, int limit) {
        String value = redis.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= limit) {
            Long ttl = redis.getExpire(key);
            throw new TooManyRequestsException("Too many failed logins, try again later",
                    ttl == null || ttl < 0 ? window.toSeconds() : ttl);
        }
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
    }

    public void reset(String email, String remoteIp) {
        redis.delete(key(email, remoteIp));
    }
}
