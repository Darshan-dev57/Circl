package com.nearmeet.identity.dto;

import com.nearmeet.identity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 60) String name,
        Role role
) {
    public SignupRequest {
        // normalise before validation runs, so " Darshan@GMAIL.com " is accepted and stored lowercase
        email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
