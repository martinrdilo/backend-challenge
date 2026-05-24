package io.backend.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rate-limit")
public record RateLimitConfig(
    int loginAttemptsPerMinute, int registerAttemptsPerMinute, int windowMinutes) {}
