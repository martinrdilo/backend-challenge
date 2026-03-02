package io.backend.notifications.dto;

import java.util.List;

public record NotificationRequest(
        Long userId,
        String title,
        String content,
        String channel,
        List<Long> attachmentIds
) {}
