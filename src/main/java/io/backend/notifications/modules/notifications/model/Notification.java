package io.backend.notifications.modules.notifications.model;

import java.time.Instant;

public record Notification(Long id,
        Long userId,
        String message,
        String title,
        String content,
        Enum<Channel> channel,
        Enum<Status> status,
        Instant createdAt) {
}
