package io.backend.notifications.unit.config;

import io.backend.notifications.config.health.ExternalApiHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExternalApiHealthIndicator}.
 *
 * <p>Verifies the health indicator pings the external photo API
 * and reports UP on success / DOWN on failure.
 */
@DisplayName("ExternalApiHealthIndicator")
class ExternalApiHealthIndicatorTest {

    @SuppressWarnings("unchecked")
    private final RestClient.RequestHeadersUriSpec uriSpec =
            mock(RestClient.RequestHeadersUriSpec.class);

    @SuppressWarnings("unchecked")
    private final RestClient.RequestHeadersSpec headersSpec =
            mock(RestClient.RequestHeadersSpec.class);

    private final RestClient.ResponseSpec responseSpec =
            mock(RestClient.ResponseSpec.class);

    private final RestClient.Builder builder = mock(RestClient.Builder.class);
    private final RestClient restClient = mock(RestClient.class);

    @BeforeEach
    void setUp() {
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    private ExternalApiHealthIndicator createIndicator() {
        return new ExternalApiHealthIndicator(builder, "https://api.example.com");
    }

    @Nested
    @DisplayName("successful health check")
    class SuccessfulHealthCheck {

        @Test
        @DisplayName("should return UP when API responds 200")
        void shouldReturnUpWhenApiResponds200() {
            when(responseSpec.toBodilessEntity())
                    .thenReturn(ResponseEntity.ok().build());

            ExternalApiHealthIndicator indicator = createIndicator();
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }

        @Test
        @DisplayName("should return UP when API responds 3xx redirect")
        void shouldReturnUpWhenApiResponds3xx() {
            when(responseSpec.toBodilessEntity())
                    .thenReturn(ResponseEntity.status(HttpStatus.FOUND).build());

            ExternalApiHealthIndicator indicator = createIndicator();
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
        }
    }

    @Nested
    @DisplayName("failed health check")
    class FailedHealthCheck {

        @Test
        @DisplayName("should return DOWN when API responds 500")
        void shouldReturnDownWhenApiResponds500() {
            when(responseSpec.toBodilessEntity())
                    .thenThrow(new RuntimeException("500 Internal Server Error"));

            ExternalApiHealthIndicator indicator = createIndicator();
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should return DOWN when API times out")
        void shouldReturnDownWhenApiTimesOut() {
            when(responseSpec.toBodilessEntity())
                    .thenThrow(new RuntimeException("Read timed out"));

            ExternalApiHealthIndicator indicator = createIndicator();
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should return DOWN when connection refused")
        void shouldReturnDownWhenConnectionRefused() {
            when(responseSpec.toBodilessEntity())
                    .thenThrow(new RuntimeException("Connection refused"));

            ExternalApiHealthIndicator indicator = createIndicator();
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }
    }
}
