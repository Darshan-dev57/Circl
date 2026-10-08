package com.darshan.circl.identity.dto;

/** Body for API clients. Browsers send the token as a cookie instead and can leave the body out. */
public record RefreshRequest(String refreshToken) {
}
