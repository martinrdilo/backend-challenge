package io.backend.notifications.unit.config;

import io.backend.notifications.config.RateLimitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "rate-limit.login-attempts-per-minute=5",
        "rate-limit.register-attempts-per-minute=3",
        "rate-limit.window-minutes=1"
})
@SuppressWarnings("unused")
class RateLimitConfigTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("notification_test_db")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RateLimitConfig rateLimitConfig;

    @Test
    @DisplayName("should bind login-attempts-per-minute from configuration")
    void shouldBindLoginAttemptsPerMinute() {
        assertThat(rateLimitConfig.loginAttemptsPerMinute()).isEqualTo(5);
    }

    @Test
    @DisplayName("should bind register-attempts-per-minute from configuration")
    void shouldBindRegisterAttemptsPerMinute() {
        assertThat(rateLimitConfig.registerAttemptsPerMinute()).isEqualTo(3);
    }

    @Test
    @DisplayName("should bind window-minutes from configuration")
    void shouldBindWindowMinutes() {
        assertThat(rateLimitConfig.windowMinutes()).isEqualTo(1);
    }
}
