package io.backend.notifications.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures a bounded {@link ThreadPoolTaskExecutor} for async event processing.
 *
 * <p>Pool sizing keeps resource consumption predictable and avoids thread explosion under load
 * spikes. The {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy} (default) rejects tasks
 * on queue overflow, providing a clear overload signal.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "notificationDispatchExecutor")
  public Executor notificationDispatchExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(25);
    executor.setThreadNamePrefix("notification-dispatch-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
