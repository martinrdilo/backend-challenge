package io.backend.notifications.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserRequest(
        @NotBlank
        String username,

        @NotBlank
        @Email
        String email
)
{}
