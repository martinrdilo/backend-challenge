package io.backend.notifications.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotBlank String phone,
    String deviceToken) {}
