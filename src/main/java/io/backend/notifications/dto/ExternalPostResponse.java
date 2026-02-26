package io.backend.notifications.dto;

public record ExternalPostResponse(
        Long userId,
        Long id,
        String title,
        String body
) {}
