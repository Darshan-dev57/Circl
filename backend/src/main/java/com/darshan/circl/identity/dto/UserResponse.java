package com.darshan.circl.identity.dto;

import com.darshan.circl.identity.Role;
import com.darshan.circl.identity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String name, Role role, int reliabilityScore, Instant createdAt) {

    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.getReliabilityScore(),
                u.getCreatedAt());
    }
}
