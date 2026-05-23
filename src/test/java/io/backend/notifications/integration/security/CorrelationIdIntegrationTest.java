package io.backend.notifications.integration.security;

import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.integration.support.MdcTestRecorder;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests proving correlation ID propagation end-to-end:
 * HTTP header → MDC → async listener via TaskDecorator.
 *
 * <p>Uses {@link MdcTestRecorder} to capture MDC context on the async
 * worker thread, proving the correlation ID survived the async boundary.
 */
@DisplayName("CorrelationIdIntegrationTest")
class CorrelationIdIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MdcTestRecorder mdcTestRecorder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        mdcTestRecorder.clear();
    }

    @Nested
    @DisplayName("correlation ID passthrough to async MDC")
    class CorrelationIdPassthrough {

        @Test
        @DisplayName("should propagate client-supplied correlation ID to async listener MDC")
        void shouldPropagateClientSuppliedCorrelationIdToAsyncMdc() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            String clientCorrelationId = "client-supplied-abc-123";

            NotificationRequest request = new NotificationRequest(
                    "Corr ID Test", "Testing correlation ID passthrough", Channel.EMAIL, List.of());

            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-Id", clientCorrelationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("PENDING");

            // Get notification ID
            var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
            var notifications = notificationRepository.findAllByUserId(user.getId());
            Long notificationId = notifications.get(0).getId();

            // Wait for async dispatch to complete
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                Notification n = notificationRepository.findById(notificationId).orElseThrow();
                return n.getStatus() == Status.SENT;
            });

            // Verify correlation ID was present in MDC during async execution
            Map<String, String> capturedMdc = mdcTestRecorder.getMdcContext(notificationId);
            assertThat(capturedMdc).isNotNull();
            assertThat(capturedMdc).containsEntry("correlationId", clientCorrelationId);
        }
    }

    @Nested
    @DisplayName("UUID generation and propagation")
    class UuidGeneration {

        @Test
        @DisplayName("should generate UUID and propagate to async MDC when no header sent")
        void shouldGenerateUuidAndPropagateToAsyncMdcWhenNoHeaderSent() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            NotificationRequest request = new NotificationRequest(
                    "UUID Test", "Testing auto-generated correlation ID", Channel.SMS, List.of());

            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("PENDING");

            var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
            var notifications = notificationRepository.findAllByUserId(user.getId());
            Long notificationId = notifications.get(0).getId();

            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                Notification n = notificationRepository.findById(notificationId).orElseThrow();
                return n.getStatus() == Status.SENT;
            });

            // Verify correlation ID was generated and propagated to async MDC
            Map<String, String> capturedMdc = mdcTestRecorder.getMdcContext(notificationId);
            assertThat(capturedMdc).isNotNull();
            assertThat(capturedMdc).containsKey("correlationId");
            // Must be a valid UUID (36 chars with hyphens)
            assertThat(capturedMdc.get("correlationId"))
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    @Nested
    @DisplayName("correlation ID present on failed dispatch")
    class FailedDispatch {

        @Test
        @DisplayName("should propagate correlation ID to async MDC even when dispatch fails")
        void shouldPropagateCorrelationIdToAsyncMdcEvenOnFailedDispatch() {
            UserBuilder builder = UserBuilder.aUser(); // no device token for PUSH
            String token = registerAndLogin(builder);

            String clientCorrelationId = "failed-dispatch-corr-456";

            // PUSH dispatch will fail because user has no device token
            NotificationRequest request = new NotificationRequest(
                    "Failed Test", "Testing correlation ID on failure", Channel.PUSH, List.of());

            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-Id", clientCorrelationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("PENDING");

            var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
            var notifications = notificationRepository.findAllByUserId(user.getId());
            Long notificationId = notifications.get(0).getId();

            // Wait for dispatch to complete (will set FAILED status)
            await().atMost(15, TimeUnit.SECONDS).until(() -> {
                Notification n = notificationRepository.findById(notificationId).orElseThrow();
                return n.getStatus() == Status.FAILED;
            });

            // Correlation ID should still be in MDC on failed dispatch path
            Map<String, String> capturedMdc = mdcTestRecorder.getMdcContext(notificationId);
            assertThat(capturedMdc).isNotNull();
            assertThat(capturedMdc).containsEntry("correlationId", clientCorrelationId);
        }
    }
}
