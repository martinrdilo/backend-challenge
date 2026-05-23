package io.backend.notifications.config.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports thread-pool health for the notification dispatch executor.
 *
 * <p>Returns {@code DOWN} when the task queue is at or above 90%
 * capacity OR when all core threads are busy (activeCount >= corePoolSize).
 */
@Component
public class DispatchHealthIndicator implements HealthIndicator {

    private final ThreadPoolTaskExecutor executor;

    public DispatchHealthIndicator(
            @Qualifier("notificationDispatchExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Health health() {
        int active = executor.getActiveCount();
        int corePoolSize = executor.getCorePoolSize();
        int poolSize = executor.getPoolSize();
        int queueSize = executor.getQueueSize();
        int queueCapacity = executor.getQueueCapacity();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("active", active);
        details.put("corePoolSize", corePoolSize);
        details.put("poolSize", poolSize);
        details.put("queueSize", queueSize);
        details.put("queueCapacity", queueCapacity);

        boolean queueSaturated = queueCapacity > 0
                && queueSize * 10L >= queueCapacity * 9L;
        boolean allCoreBusy = active >= corePoolSize;

        if (queueSaturated || allCoreBusy) {
            return Health.down().withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }
}
