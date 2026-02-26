package io.backend.notifications.integration.base;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests.
 *
 * Provides:
 * - Singleton PostgreSQL container (Testcontainers) — shared across all test classes
 * - WireMock server on dynamic port — for mocking external API calls
 * - Dynamic property overrides for datasource and external API base-url
 * - WebTestClient for HTTP request testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Singleton Testcontainers PostgreSQL — started once, reused by all tests
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("notification_test_db")
                    .withUsername("test")
                    .withPassword("test");

    // Singleton WireMock server — started once, reused by all tests
    protected static final WireMockServer WIREMOCK =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Database — point to Testcontainers PostgreSQL
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // External API — point to WireMock (tests match on path only)
        registry.add("external.api.jsonplaceholder.base-url",
                () -> "http://localhost:" + WIREMOCK.port());
    }

    @Autowired
    protected WebTestClient webTestClient;
}
