package com.nearmeet.common.web;

import com.nearmeet.common.error.UnauthorizedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/** Reads who is calling from the verified JWT. Services get the id, never the token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("Missing access token");
        }
        return UUID.fromString(jwt.getSubject());
    }

    public static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt == null ? List.of() : jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
