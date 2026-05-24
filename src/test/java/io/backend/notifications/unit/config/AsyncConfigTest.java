package io.backend.notifications.unit.config;

import io.backend.notifications.config.AsyncConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AsyncConfig}.
 *
 * <p>Verifies thread pool configuration, MDC propagation
 * via TaskDecorator, and Micrometer gauge registration.
 */
@DisplayName("AsyncConfig")
class AsyncConfigTest {

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    @DisplayName("should configure executor with correct pool settings")
    void shouldConfigureExecutorWithCorrectPoolSettings() {
        AsyncConfig config = new AsyncConfig(meterRegistry);
        ThreadPoolTaskExecutor executor = config.notificationDispatchExecutor();

        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getQueueCapacity()).isEqualTo(25);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("notification-dispatch-");
    }

    @Nested
    @DisplayName("MDC propagation via TaskDecorator")
    class MdcPropagation {

        @BeforeEach
        void clearMdcBefore() {
            MDC.clear();
        }

        @AfterEach
        void clearMdcAfter() {
            MDC.clear();
        }

        @Test
        @DisplayName("should propagate MDC context to worker thread")
        void shouldPropagateMdcContextToWorkerThread() throws Exception {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            ThreadPoolTaskExecutor executor = config.notificationDispatchExecutor();

            MDC.put("correlationId", "test-corr-123");
            MDC.put("userId", "user-1");

            CountDownLatch latch = new CountDownLatch(1);
            Map<String, String>[] capturedContext = new Map[1];

            executor.execute(() -> {
                capturedContext[0] = MDC.getCopyOfContextMap();
                latch.countDown();
            });

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedContext[0])
                    .isNotNull()
                    .containsEntry("correlationId", "test-corr-123")
                    .containsEntry("userId", "user-1");
        }

        @Test
        @DisplayName("should clear MDC on worker thread after task completes")
        void shouldClearMdcOnWorkerThreadAfterTask() throws Exception {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            ThreadPoolTaskExecutor executor = config.notificationDispatchExecutor();

            MDC.put("correlationId", "test-corr-clear");

            CountDownLatch latch = new CountDownLatch(1);
            executor.execute(() -> {
                // Task runs with MDC set, then the decorator clears it in finally
                latch.countDown();
            });

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            // Clear MDC on submitter thread so next task captures null context
            MDC.clear();

            // Submit another task — should not see any MDC from previous task
            CountDownLatch latch2 = new CountDownLatch(1);
            Map<String, String>[] contextAfterTask = new Map[1];
            executor.execute(() -> {
                contextAfterTask[0] = MDC.getCopyOfContextMap();
                latch2.countDown();
            });

            assertThat(latch2.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(contextAfterTask[0]).isNull();
        }

        @Test
        @DisplayName("should handle null MDC context gracefully")
        void shouldHandleNullMdcContextGracefully() throws Exception {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            ThreadPoolTaskExecutor executor = config.notificationDispatchExecutor();

            MDC.clear(); // Ensure MDC is empty

            CountDownLatch latch = new CountDownLatch(1);
            executor.execute(() -> latch.countDown());

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            // No exception thrown = success
        }
    }

    @Nested
    @DisplayName("Micrometer gauge registration")
    class GaugeRegistration {

        @Test
        @DisplayName("should register queue.remaining gauge")
        void shouldRegisterQueueRemainingGauge() {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            config.notificationDispatchExecutor();

            assertThat(meterRegistry.find("executor.queue.remaining").gauge())
                    .isNotNull();
        }

        @Test
        @DisplayName("should register active threads gauge")
        void shouldRegisterActiveGauge() {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            config.notificationDispatchExecutor();

            assertThat(meterRegistry.find("executor.active").gauge())
                    .isNotNull();
        }

        @Test
        @DisplayName("should register pool size gauge")
        void shouldRegisterPoolSizeGauge() {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            config.notificationDispatchExecutor();

            assertThat(meterRegistry.find("executor.pool.size").gauge())
                    .isNotNull();
        }

        @Test
        @DisplayName("should report accurate queue remaining value")
        void shouldReportAccurateQueueRemainingValue() {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            config.notificationDispatchExecutor();

            double remaining = meterRegistry.find("executor.queue.remaining")
                    .gauge().value();

            // Queue capacity 25 - 0 tasks queued = 25 remaining
            assertThat(remaining).isEqualTo(25.0);
        }

        @Test
        @DisplayName("should report accurate active count")
        void shouldReportAccurateActiveCount() {
            AsyncConfig config = new AsyncConfig(meterRegistry);
            config.notificationDispatchExecutor();

            double active = meterRegistry.find("executor.active")
                    .gauge().value();

            assertThat(active).isEqualTo(0.0);
        }
    }
}
