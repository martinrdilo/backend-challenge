package io.backend.notifications.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;

public record Notification(
        @Id Long id,
        Long userId,
        String message,
        String title,
        String content,
        Enum<Channel> channel,
        Enum<Status> status,
        Instant createdAt) {
}
