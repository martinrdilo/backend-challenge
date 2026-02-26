package io.backend.notifications.client;

import io.backend.notifications.dto.ExternalPostResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class ExternalPostClient {

    private final RestClient restClient;

    public ExternalPostClient(
            RestClient.Builder builder,
            @Value("${external.api.jsonplaceholder.base-url}") String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public List<ExternalPostResponse> getPostsByUser(Long userId) {
        ExternalPostResponse[] posts = restClient.get()
                .uri("/posts?userId={userId}", userId)
                .retrieve()
                .body(ExternalPostResponse[].class);

        return posts != null ? List.of(posts) : List.of();
    }
}
