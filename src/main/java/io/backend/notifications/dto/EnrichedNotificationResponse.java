package io.backend.notifications.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EnrichedNotificationResponse(
        Long id,
        String title,
        String content,
        String channel,
        String status,
        LocalDateTime createdAt,
        Long userId,
        List<ExternalPhotoResponse> attachments
) {}
