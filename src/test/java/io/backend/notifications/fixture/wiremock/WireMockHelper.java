package io.backend.notifications.fixture.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Helper for setting up WireMock stubs for external API calls.
 *
 * All stubs match on PATH only (urlPathEqualTo), making them URL-agnostic.
 * Whether the base URL is localhost or api.jsonplaceholder.com,
 * stubs will match as long as the path after "/" is correct.
 */
public final class WireMockHelper {

    private WireMockHelper() {
    }

    /**
     * Stub GET /posts to return a list of posts for a given userId.
     * Matches: any base URL + /posts with query param userId.
     */
    public static void stubGetPostsByUser(WireMockServer server, long userId, String responseBody) {
        server.stubFor(WireMock.get(urlPathEqualTo("/posts"))
                .withQueryParam("userId", WireMock.equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    /**
     * Stub GET /posts to return an empty list for a given userId.
     */
    public static void stubGetPostsByUserEmpty(WireMockServer server, long userId) {
        stubGetPostsByUser(server, userId, "[]");
    }

    /**
     * Stub GET /posts to return a server error for a given userId.
     */
    public static void stubGetPostsByUserError(WireMockServer server, long userId) {
        server.stubFor(WireMock.get(urlPathEqualTo("/posts"))
                .withQueryParam("userId", WireMock.equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Internal Server Error\"}")));
    }

    /**
     * Stub GET /posts to simulate a timeout for a given userId.
     */
    public static void stubGetPostsByUserTimeout(WireMockServer server, long userId, int delayMs) {
        server.stubFor(WireMock.get(urlPathEqualTo("/posts"))
                .withQueryParam("userId", WireMock.equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(delayMs)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
    }

    /**
     * Reset all WireMock stubs and request logs.
     * Call this in @BeforeEach to ensure test isolation.
     */
    public static void reset(WireMockServer server) {
        server.resetAll();
    }
}
