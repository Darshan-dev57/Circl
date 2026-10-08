package com.darshan.circl.identity;

import com.darshan.circl.common.error.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    private static String key(String email) {
        return "login:fail:" + email;
    }

    public void checkAllowed(String email) {
        String value = redis.opsForValue().get(key(email));
        if (value != null && Integer.parseInt(value) >= maxFailures) {
            Long ttl = redis.getExpire(key(email));
            throw new TooManyRequestsException("Too many failed logins, try again later",
                    ttl == null || ttl < 0 ? window.toSeconds() : ttl);
        }
    }

    public void recordFailure(String email) {
        Long count = redis.opsForValue().increment(key(email));
        if (count != null && count == 1) {
            redis.expire(key(email), window);
        }
    }

    public void reset(String email) {
        redis.delete(key(email));
    }
}
