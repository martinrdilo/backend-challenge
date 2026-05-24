package io.backend.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record NotificationUpdateRequest(
    @NotBlank String title, @NotBlank String content, List<Long> attachmentIds) {}
