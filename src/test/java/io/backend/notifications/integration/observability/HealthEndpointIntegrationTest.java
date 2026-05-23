package io.backend.notifications.integration.observability;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for Actuator health endpoint.
 * Proves that actuator dependency and config are correctly wired (Phase 1 foundation).
 */
@DisplayName("GET /actuator/health")
class HealthEndpointIntegrationTest extends AbstractIntegrationTest {

    @Nested
    @DisplayName("with authentication")
    class WithAuthentication {

        @Test
        @DisplayName("returns 200 UP when actuator is configured")
        void shouldReturnUpStatusWhenAuthenticated() {
            // Stub external API so ExternalApiHealthIndicator reports UP
            WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/posts/1"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            UserBuilder builder = UserBuilder.aUser();
            String token = registerAndLogin(builder);

            webTestClient().get()
                    .uri("/actuator/health")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }
    }

    @Nested
    @DisplayName("without authentication")
    class WithoutAuthentication {

        @Test
        @DisplayName("returns 200 without token (K8s liveness convention)")
        void shouldReturn200WithoutToken() {
            // Stub external API so ExternalApiHealthIndicator reports UP
            WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/posts/1"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            webTestClient().get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }
    }
}
