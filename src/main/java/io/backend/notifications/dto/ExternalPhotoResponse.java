package io.backend.notifications.dto;

public record ExternalPhotoResponse(
        Long albumId,
        Long id,
        String title,
        String url,
        String thumbnailUrl
) {}
