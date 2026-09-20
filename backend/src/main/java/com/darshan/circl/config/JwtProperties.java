package com.darshan.circl.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties("circl.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl
) {
    public JwtProperties {
        // HS256 needs at least 256 bits of key; refuse to start with a weak one
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("circl.jwt.secret (CIRCL_JWT_SECRET) must be at least 32 bytes");
        }
    }
}
