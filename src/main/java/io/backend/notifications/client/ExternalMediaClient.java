package io.backend.notifications.client;

import io.backend.notifications.dto.ExternalPhotoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExternalMediaClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalMediaClient.class);

    private final RestClient restClient;

    public ExternalMediaClient(
            RestClient.Builder builder,
            @Value("${external.api.photos.base-url}") String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public ExternalPhotoResponse getPhotoById(Long id) {
        try {
            return restClient.get()
                    .uri("/photos/{id}", id)
                    .retrieve()
                    .body(ExternalPhotoResponse.class);
        } catch (RuntimeException e) {
            log.error("Failed to fetch photo with id {}: {}", id, e.getMessage());
            return null;
        }
    }
}
