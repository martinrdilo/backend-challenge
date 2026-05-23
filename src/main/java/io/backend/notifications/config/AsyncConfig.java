package io.backend.notifications.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures a bounded {@link ThreadPoolTaskExecutor} for async event processing.
 *
 * <p>Pool sizing keeps resource consumption predictable and avoids thread
 * explosion under load spikes. The {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy}
 * (default) rejects tasks on queue overflow, providing a clear overload signal.
 *
 * <p>The configured {@code TaskDecorator} captures MDC context at task
 * submission and restores it on the worker thread, ensuring correlation IDs
 * survive the async boundary.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private final MeterRegistry meterRegistry;

    public AsyncConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean(name = "notificationDispatchExecutor")
    public ThreadPoolTaskExecutor notificationDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("notification-dispatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.setTaskDecorator(task -> {
            var ctx = MDC.getCopyOfContextMap();
            return () -> {
                if (ctx != null) {
                    MDC.setContextMap(ctx);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            };
        });

        executor.initialize();

        // Register Micrometer gauges for thread pool observability
        meterRegistry.gauge("executor.queue.remaining", executor,
                e -> e.getQueueCapacity() - e.getQueueSize());
        meterRegistry.gauge("executor.active", executor,
                ThreadPoolTaskExecutor::getActiveCount);
        meterRegistry.gauge("executor.pool.size", executor,
                ThreadPoolTaskExecutor::getPoolSize);

        return executor;
    }
}
