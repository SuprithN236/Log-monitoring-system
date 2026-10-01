package com.logmonitoring.engine.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for both sign-up and sign-in. BCrypt ignores input past 72 bytes, so longer passwords are refused. */
public record AuthRequest(

        @NotBlank
        @Email(message = "must be a valid email address")
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password
) {
}
