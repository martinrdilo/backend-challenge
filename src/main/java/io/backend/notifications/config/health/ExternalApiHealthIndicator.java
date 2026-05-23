package io.backend.notifications.config.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Pings the external photo API and reports health based on
 * the HTTP response status.
 *
 * <p>Returns {@code UP} for 2xx/3xx responses.
 * Returns {@code DOWN} for connectivity errors, timeouts,
 * or server errors.
 */
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiHealthIndicator.class);

    private final RestClient restClient;

    public ExternalApiHealthIndicator(
            RestClient.Builder builder,
            @Value("${external.api.photos.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public Health health() {
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri("/posts/1")
                    .retrieve()
                    .toBodilessEntity();

            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            if (status.is2xxSuccessful() || status.is3xxRedirection()) {
                return Health.up().build();
            }
            return Health.down()
                    .withDetail("status", status.value())
                    .build();
        } catch (Exception e) {
            log.warn("External API health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
