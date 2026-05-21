package io.backend.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry and binds externalized retry configuration
 * from the {@code notifications.dispatch.retry} namespace.
 */
@Configuration
@EnableRetry
@ConfigurationProperties(prefix = "notifications.dispatch.retry")
public class RetryConfig {

    /** Maximum number of retry attempts (default 3). */
    private int maxAttempts = 3;

    /** Base delay between retries in milliseconds (default 1000). */
    private long delayMs = 1000;

    /** Exponential backoff multiplier (default 2.0). */
    private double multiplier = 2.0;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }
}
