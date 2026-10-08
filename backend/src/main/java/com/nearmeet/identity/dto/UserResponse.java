package com.nearmeet.identity.dto;

import com.nearmeet.identity.Role;
import com.nearmeet.identity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String name, Role role, int reliabilityScore, Instant createdAt) {

    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.getReliabilityScore(),
                u.getCreatedAt());
    }
}
