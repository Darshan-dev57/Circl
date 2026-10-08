package com.darshan.circl.identity;

import com.darshan.circl.config.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The browser keeps the refresh token in an HttpOnly cookie, so a script running later on the page
 * cannot read it. The web app ignores the copy in the login response body (that one is for Postman
 * and other API clients). SameSite=Strict and a path limited to /api/v1/auth mean other sites cannot
 * make the browser send it.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "circl_refresh";
    private static final String PATH = "/api/v1/auth";

    private final Duration maxAge;
    private final boolean secure;

    public RefreshCookie(JwtProperties jwt, @Value("${circl.auth.cookie-secure:false}") boolean secure) {
        this.maxAge = jwt.refreshTtl();
        this.secure = secure;
    }

    public String issue(String refreshToken) {
        return build(refreshToken, maxAge);
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    private String build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(age)
                .build()
                .toString();
    }
}
