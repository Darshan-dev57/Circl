package com.darshan.circl.identity.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {
}
