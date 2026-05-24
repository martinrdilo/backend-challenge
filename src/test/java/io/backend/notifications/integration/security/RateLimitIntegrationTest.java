package io.backend.notifications.integration.security;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.backend.notifications.dto.LoginRequest;
import io.backend.notifications.dto.RegisterRequest;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("RateLimit Integration")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

  private static final int LOGIN_LIMIT = 5;
  private static final int REGISTER_LIMIT = 3;

  @DynamicPropertySource
  static void overrideRateLimits(DynamicPropertyRegistry registry) {
    registry.add("rate-limit.login-attempts-per-minute", () -> LOGIN_LIMIT);
    registry.add("rate-limit.register-attempts-per-minute", () -> REGISTER_LIMIT);
    registry.add("rate-limit.window-minutes", () -> 1);
  }

  @BeforeEach
  void setUp() {
    cleanDatabase();
  }

  @Test
  @DisplayName("should allow login up to limit then return 429 ProblemDetail with Retry-After")
  void shouldReturn429ProblemDetailWhenLoginLimitExceeded() {
    LoginRequest request = new LoginRequest("unknown@test.com", "password123");
    String clientIp = "192.168.1.1";

    // First LOGIN_LIMIT requests should succeed (return 401 — invalid credentials, but NOT 429)
    for (int i = 0; i < LOGIN_LIMIT; i++) {
      webTestClient()
          .post()
          .uri("/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .header("X-Forwarded-For", clientIp)
          .exchange()
          .expectStatus()
          .isUnauthorized();
    }

    // The next request should be rate-limited
    webTestClient()
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .header("X-Forwarded-For", clientIp)
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.type")
        .exists()
        .jsonPath("$.title")
        .value(is("Too Many Requests"))
        .jsonPath("$.status")
        .value(equalTo(429))
        .jsonPath("$.detail")
        .exists()
        .jsonPath("$.instance")
        .exists()
        .jsonPath("$.retryAfterSeconds")
        .exists();
  }

  @Test
  @DisplayName("should allow register up to limit then return 429 ProblemDetail with Retry-After")
  void shouldReturn429ProblemDetailWhenRegisterLimitExceeded() {
    String clientIp = "192.168.2.2";

    // First REGISTER_LIMIT requests should succeed (unique emails)
    for (int i = 0; i < REGISTER_LIMIT; i++) {
      RegisterRequest request =
          UserBuilder.aUser().withEmail("reg" + i + "@test.com").buildRegisterRequest();

      webTestClient()
          .post()
          .uri("/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .header("X-Forwarded-For", clientIp)
          .exchange()
          .expectStatus()
          .isCreated();
    }

    // The next request should be rate-limited
    RegisterRequest blockedRequest =
        UserBuilder.aUser().withEmail("blocked@test.com").buildRegisterRequest();

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(blockedRequest)
        .header("X-Forwarded-For", clientIp)
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.type")
        .exists()
        .jsonPath("$.title")
        .value(is("Too Many Requests"))
        .jsonPath("$.status")
        .value(equalTo(429))
        .jsonPath("$.detail")
        .exists()
        .jsonPath("$.instance")
        .exists()
        .jsonPath("$.retryAfterSeconds")
        .exists();
  }

  @Test
  @DisplayName(
      "should isolate login and register buckets — login exhaustion does not block register")
  void shouldIsolateLoginAndRegisterBuckets() {
    String clientIp = "192.168.3.3";

    // Exhaust the login bucket
    LoginRequest loginRequest = new LoginRequest("unknown@test.com", "password123");
    for (int i = 0; i < LOGIN_LIMIT; i++) {
      webTestClient()
          .post()
          .uri("/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(loginRequest)
          .header("X-Forwarded-For", clientIp)
          .exchange()
          .expectStatus()
          .isUnauthorized();
    }

    // 6th login should be 429
    webTestClient()
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(loginRequest)
        .header("X-Forwarded-For", clientIp)
        .exchange()
        .expectStatus()
        .isEqualTo(429);

    // Register should still work (separate bucket)
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(UserBuilder.aUser().withEmail("fresh@test.com").buildRegisterRequest())
        .header("X-Forwarded-For", clientIp)
        .exchange()
        .expectStatus()
        .isCreated();
  }

  @Test
  @DisplayName("should use X-Forwarded-For header for IP detection")
  void shouldUseXForwardedForHeader() {
    String blockedIp = "10.0.0.1";
    String allowedIp = "10.0.0.2";

    // Exhaust register bucket via one IP
    for (int i = 0; i < REGISTER_LIMIT; i++) {
      RegisterRequest request =
          UserBuilder.aUser().withEmail("ip1-user" + i + "@test.com").buildRegisterRequest();

      webTestClient()
          .post()
          .uri("/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Forwarded-For", blockedIp)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isCreated();
    }

    // That IP should now be rate-limited
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Forwarded-For", blockedIp)
        .bodyValue(UserBuilder.aUser().withEmail("blocked-ip1@test.com").buildRegisterRequest())
        .exchange()
        .expectStatus()
        .isEqualTo(429);

    // A different IP should still be able to register
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Forwarded-For", allowedIp)
        .bodyValue(UserBuilder.aUser().withEmail("ip2-user@test.com").buildRegisterRequest())
        .exchange()
        .expectStatus()
        .isCreated();
  }
}
