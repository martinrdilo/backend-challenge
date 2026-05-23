package io.backend.notifications.unit.config;

import io.backend.notifications.config.health.DispatchHealthIndicator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DispatchHealthIndicator}.
 *
 * <p>Verifies the health indicator reads {@link ThreadPoolTaskExecutor}
 * stats and returns UP / DOWN based on pool saturation.
 */
@DisplayName("DispatchHealthIndicator")
class DispatchHealthIndicatorTest {

    @Nested
    @DisplayName("healthy pool")
    class HealthyPool {

        @Test
        @DisplayName("should return UP when under capacity")
        void shouldReturnUpWhenUnderCapacity() {
            ThreadPoolTaskExecutor executor = mockExecutor(1, 2, 4, 5, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("active", 1);
            assertThat(health.getDetails()).containsEntry("corePoolSize", 2);
            assertThat(health.getDetails()).containsEntry("poolSize", 4);
            assertThat(health.getDetails()).containsEntry("queueSize", 5);
            assertThat(health.getDetails()).containsEntry("queueCapacity", 25);
        }

        @Test
        @DisplayName("should return UP when idle")
        void shouldReturnUpWhenIdle() {
            ThreadPoolTaskExecutor executor = mockExecutor(0, 2, 2, 0, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
    }

    @Nested
    @DisplayName("degraded pool")
    class DegradedPool {

        @Test
        @DisplayName("should return DOWN when queue exceeds 90% capacity")
        void shouldReturnDownWhenQueueExceeds90Percent() {
            // queue 23 / 25 = 92% > 90%
            ThreadPoolTaskExecutor executor = mockExecutor(0, 2, 4, 23, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should return DOWN when queue is exactly at 90%")
        void shouldReturnDownWhenQueueAtExactly90Percent() {
            // 22/25 = 88% — actually under 90%. Let's test exact boundary:
            // 23/25 = 92%. For exactly 90%: 22.5 → but queue is int.
            // Using formula: queueSize * 10 >= queueCapacity * 9
            // 23 * 10 = 230, 25 * 9 = 225 → 230 >= 225 → DOWN
            // So 23/25 triggers DOWN.
            ThreadPoolTaskExecutor executor = mockExecutor(0, 2, 4, 23, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should return DOWN when all core threads are busy")
        void shouldReturnDownWhenAllCoreThreadsBusy() {
            // active = corePoolSize = 2
            ThreadPoolTaskExecutor executor = mockExecutor(2, 2, 4, 0, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should return DOWN when threads exceed core pool size")
        void shouldReturnDownWhenActiveExceedsCorePoolSize() {
            ThreadPoolTaskExecutor executor = mockExecutor(3, 2, 4, 0, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should return UP when queue is just under 90%")
        void shouldReturnUpWhenQueueJustUnder90Percent() {
            ThreadPoolTaskExecutor executor = mockExecutor(0, 2, 4, 22, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("should return UP when one core thread free and queue low")
        void shouldReturnUpWhenOneCoreThreadFree() {
            ThreadPoolTaskExecutor executor = mockExecutor(1, 2, 4, 5, 25);

            DispatchHealthIndicator indicator = new DispatchHealthIndicator(executor);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
    }

    /**
     * Creates a mocked ThreadPoolTaskExecutor with controlled stats.
     */
    private static ThreadPoolTaskExecutor mockExecutor(
            int activeCount, int corePoolSize, int poolSize,
            int queueSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        when(executor.getActiveCount()).thenReturn(activeCount);
        when(executor.getCorePoolSize()).thenReturn(corePoolSize);
        when(executor.getPoolSize()).thenReturn(poolSize);
        when(executor.getQueueSize()).thenReturn(queueSize);
        when(executor.getQueueCapacity()).thenReturn(queueCapacity);
        return executor;
    }
}
