package io.backend.notifications.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.dto.LoginRequest;
import io.backend.notifications.dto.RegisterRequest;
import io.backend.notifications.entity.User;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Integration tests for AuthController. Covers register and login scenarios from spec. */
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    cleanDatabase();
  }

  @Test
  void shouldRegisterSuccessfullyAndReturn201() {
    RegisterRequest request = UserBuilder.aUser().buildRegisterRequest();

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.token")
        .isNotEmpty();
  }

  @Test
  void shouldReturn409OnDuplicateEmail() {
    RegisterRequest request = UserBuilder.aUser().buildRegisterRequest();

    // Register first time
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isCreated();

    // Register again with same email
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void shouldReturn400OnInvalidRegistrationInput() {
    // Missing username, invalid email, short password
    RegisterRequest invalid = new RegisterRequest("", "not-an-email", "short", null, null);

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalid)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldReturn400OnBlankPasswordAtRegistration() {
    RegisterRequest invalid =
        new RegisterRequest("validuser", "valid@test.com", "1234567", null, null);

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalid)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldLoginSuccessfullyAndReturn200() {
    UserBuilder builder = UserBuilder.aUser();
    RegisterRequest reg = builder.buildRegisterRequest();

    // Register first
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(reg)
        .exchange()
        .expectStatus()
        .isCreated();

    // Login
    webTestClient()
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new LoginRequest(builder.getEmail(), builder.getPassword()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.token")
        .isNotEmpty();
  }

  @Test
  void shouldReturn401OnWrongPassword() {
    UserBuilder builder = UserBuilder.aUser();

    // Register
    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(builder.buildRegisterRequest())
        .exchange()
        .expectStatus()
        .isCreated();

    // Login with wrong password
    webTestClient()
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new LoginRequest(builder.getEmail(), "wrongpassword"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldReturn401OnUnknownEmail() {
    webTestClient()
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new LoginRequest("unknown@test.com", "password123"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void shouldReturn400OnMissingLoginFields() {
    webTestClient()
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new LoginRequest("", ""))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldReturn400WhenPhoneIsNull() {
    RegisterRequest request =
        new RegisterRequest("validuser", "valid@test.com", "password123", null, null);

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldReturn400WhenPhoneIsEmpty() {
    RegisterRequest request =
        new RegisterRequest("validuser", "valid@test.com", "password123", "", null);

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldReturn400WhenPhoneIsBlank() {
    RegisterRequest request =
        new RegisterRequest("validuser", "valid@test.com", "password123", "   ", null);

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void shouldPersistPhoneAndDeviceTokenOnRegistration() {
    UserBuilder builder =
        UserBuilder.aUser().withPhone("+5491112345678").withDeviceToken("tok_abc123");

    webTestClient()
        .post()
        .uri("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(builder.buildRegisterRequest())
        .exchange()
        .expectStatus()
        .isCreated();

    User user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
    assertThat(user.getPhone()).isEqualTo("+5491112345678");
    assertThat(user.getDeviceToken()).isEqualTo("tok_abc123");
  }
}
