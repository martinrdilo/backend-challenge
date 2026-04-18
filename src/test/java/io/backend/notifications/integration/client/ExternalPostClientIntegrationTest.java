package io.backend.notifications.integration.client;

import io.backend.notifications.client.ExternalPostClient;
import io.backend.notifications.dto.ExternalPostResponse;
import io.backend.notifications.fixture.dto.ExternalPostResponseBuilder;
import io.backend.notifications.fixture.wiremock.WireMockHelper;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for ExternalPostClient.
 * Proves WireMock URL-agnostic matching works:
 * - base-url is overridden to WireMock via @DynamicPropertySource
 * - stubs match on PATH only (/posts), not full URL
 * - no real HTTP calls to jsonplaceholder.typicode.com
 */
class ExternalPostClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ExternalPostClient externalPostClient;

    @Test
    void shouldReturnPostsFromWireMock() {
        String json = ExternalPostResponseBuilder.aPost()
                .withUserId(1L)
                .buildListJson(3);
        WireMockHelper.stubGetPostsByUser(WIREMOCK, 1L, json);

        List<ExternalPostResponse> result = externalPostClient.getPostsByUser(1L);

        assertThat(result).hasSize(3);
        assertThat(result.getFirst().userId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyListWhenNoPostsFound() {
        WireMockHelper.stubGetPostsByUserEmpty(WIREMOCK, 5L);

        List<ExternalPostResponse> result = externalPostClient.getPostsByUser(5L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowWhenExternalApiReturnsError() {
        WireMockHelper.stubGetPostsByUserError(WIREMOCK, 1L);

        assertThatThrownBy(() -> externalPostClient.getPostsByUser(1L))
                .isInstanceOf(RestClientException.class);
    }
}
