package io.backend.notifications.integration.observability;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Actuator health and metrics endpoints.
 *
 * <p>Proves that custom health indicators (dispatch, externalApi) report UP,
 * the database indicator is auto-registered, health is public, and metrics
 * endpoints require authentication.
 */
@DisplayName("Health and Metrics Integration")
class HealthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUpMocks() {
        // Stub external API so ExternalApiHealthIndicator reports UP
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/posts/1"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    @Nested
    @DisplayName("GET /actuator/health")
    class HealthEndpoint {

        @Test
        @DisplayName("returns UP status without authentication")
        void shouldReturnUpWithoutAuth() {
            webTestClient().get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("returns UP with dispatch component details when authenticated")
        void shouldIncludeDispatchComponentWhenAuthenticated() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            webTestClient().get()
                    .uri("/actuator/health")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP")
                    .jsonPath("$.components.dispatch").exists()
                    .jsonPath("$.components.dispatch.status").isEqualTo("UP")
                    .jsonPath("$.components.dispatch.details.active").exists()
                    .jsonPath("$.components.dispatch.details.corePoolSize").isEqualTo(2);
        }

        @Test
        @DisplayName("returns UP with externalApi component details when authenticated")
        void shouldIncludeExternalApiComponentWhenAuthenticated() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            webTestClient().get()
                    .uri("/actuator/health")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP")
                    .jsonPath("$.components.externalApi").exists()
                    .jsonPath("$.components.externalApi.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("returns UP with database component details when authenticated")
        void shouldIncludeDatabaseComponentWhenAuthenticated() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            webTestClient().get()
                    .uri("/actuator/health")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP")
                    .jsonPath("$.components.db").exists()
                    .jsonPath("$.components.db.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("hides component details for unauthenticated requests")
        void shouldHideDetailsForUnauthenticatedRequests() {
            webTestClient().get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP")
                    .jsonPath("$.components").doesNotExist();
        }

        @Test
        @DisplayName("should report healthy thread pool when idle")
        void shouldReportHealthyThreadPoolWhenIdle() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            webTestClient().get()
                    .uri("/actuator/health")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.components.dispatch.status").isEqualTo("UP")
                    .jsonPath("$.components.dispatch.details.active").isEqualTo(0)
                    .jsonPath("$.components.dispatch.details.corePoolSize").isEqualTo(2)
                    .jsonPath("$.components.dispatch.details.queueCapacity").isEqualTo(25)
                    .jsonPath("$.components.dispatch.details.queueSize").isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("GET /actuator/metrics")
    class MetricsEndpoint {

        @Test
        @DisplayName("returns 401 without authentication")
        void shouldReturn401WithoutAuth() {
            webTestClient().get()
                    .uri("/actuator/metrics")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("returns metrics list when authenticated")
        void shouldReturnMetricsListWhenAuthenticated() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            webTestClient().get()
                    .uri("/actuator/metrics")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.names").isArray()
                    .jsonPath("$.names").isNotEmpty();
        }
    }

    @Nested
    @DisplayName("GET /actuator/metrics/notification.dispatched")
    class NotificationDispatchedMetric {

        @Test
        @DisplayName("returns 401 without authentication")
        void shouldReturn401WithoutAuth() {
            webTestClient().get()
                    .uri("/actuator/metrics/notification.dispatched")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("returns metric data when authenticated after a dispatch")
        void shouldReturnMetricDataAfterDispatch() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            // First, dispatch a notification to register the counter
            NotificationRequest request = new NotificationRequest(
                    "Metrics Test", "Dispatch to register counter", Channel.EMAIL, List.of());
            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated();

            var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
            var notifications = notificationRepository.findAllByUserId(user.getId());
            Long notificationId = notifications.get(0).getId();

            // Wait for async dispatch
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                Notification n = notificationRepository.findById(notificationId).orElseThrow();
                return n.getStatus() == Status.SENT;
            });

            // Now the counter should be registered and queryable
            webTestClient().get()
                    .uri("/actuator/metrics/notification.dispatched")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("notification.dispatched")
                    .jsonPath("$.measurements").isArray()
                    .jsonPath("$.measurements[0].value").isNumber()
                    .jsonPath("$.availableTags").isArray();
        }
    }

    @Nested
    @DisplayName("GET /actuator/metrics/notification.retry.total")
    class NotificationRetryTotalMetric {

        @Test
        @DisplayName("returns 401 without authentication")
        void shouldReturn401WithoutAuth() {
            webTestClient().get()
                    .uri("/actuator/metrics/notification.retry.total")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("returns metric data when authenticated after a failed dispatch")
        void shouldReturnMetricDataAfterFailedDispatch() {
            UserBuilder builder = UserBuilder.aUser(); // no device token — PUSH will fail
            String token = registerAndLogin(builder);

            // Trigger a failed dispatch to register the retry counter
            NotificationRequest request = new NotificationRequest(
                    "Retry Metric Test", "Will fail and register retry counter", Channel.PUSH, List.of());
            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated();

            var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
            var notifications = notificationRepository.findAllByUserId(user.getId());
            Long notificationId = notifications.get(0).getId();

            await().atMost(15, TimeUnit.SECONDS).until(() -> {
                Notification n = notificationRepository.findById(notificationId).orElseThrow();
                return n.getStatus() == Status.FAILED;
            });

            // Retry counter should be registered after a failed dispatch
            webTestClient().get()
                    .uri("/actuator/metrics/notification.retry.total")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("notification.retry.total")
                    .jsonPath("$.measurements").isArray()
                    .jsonPath("$.measurements[0].value").isNumber();
        }
    }

    @Nested
    @DisplayName("GET /actuator/metrics/notification.dispatch.duration")
    class NotificationDispatchDurationMetric {

        @Test
        @DisplayName("returns 401 without authentication")
        void shouldReturn401WithoutAuth() {
            webTestClient().get()
                    .uri("/actuator/metrics/notification.dispatch.duration")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("returns metric data when authenticated after a dispatch")
        void shouldReturnMetricDataAfterDispatch() {
            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            // Dispatch a notification to register the timer
            NotificationRequest request = new NotificationRequest(
                    "Timer Metric Test", "Dispatch to register timer", Channel.SMS, List.of());
            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated();

            var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
            var notifications = notificationRepository.findAllByUserId(user.getId());
            Long notificationId = notifications.get(0).getId();

            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                Notification n = notificationRepository.findById(notificationId).orElseThrow();
                return n.getStatus() == Status.SENT;
            });

            webTestClient().get()
                    .uri("/actuator/metrics/notification.dispatch.duration")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.name").isEqualTo("notification.dispatch.duration")
                    .jsonPath("$.measurements").isArray()
                    .jsonPath("$.measurements[0].value").isNumber();
        }
    }
}
