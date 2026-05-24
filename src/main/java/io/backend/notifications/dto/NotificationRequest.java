package io.backend.notifications.dto;

import io.backend.notifications.enums.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NotificationRequest(
    @NotBlank String title,
    @NotBlank String content,
    @NotNull Channel channel,
    List<Long> attachmentIds) {}
