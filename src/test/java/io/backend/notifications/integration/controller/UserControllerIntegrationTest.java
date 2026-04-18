package io.backend.notifications.integration.controller;

import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.entity.User;
import io.backend.notifications.fixture.dto.ExternalPostResponseBuilder;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.fixture.wiremock.WireMockHelper;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for UserController.
 * Proves full stack works: HTTP request -> Controller -> Service -> Repository -> DB
 * and WireMock intercepts external API calls.
 */
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateUser() {
        UserRequest request = UserBuilder.aUser().buildRequest();

        webTestClient().post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.username").isEqualTo(request.username())
                .jsonPath("$.email").isEqualTo(request.email());
    }

    @Test
    void shouldGetAllUsers() {
        User user = userRepository.save(UserBuilder.aUser().build());

        webTestClient().get()
                .uri("/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].username").isEqualTo(user.getUsername());
    }

    @Test
    void shouldGetUserById() {
        User user = userRepository.save(UserBuilder.aUser().build());

        webTestClient().get()
                .uri("/users/{id}", user.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo(user.getUsername())
                .jsonPath("$.email").isEqualTo(user.getEmail());
    }

    @Test
    void shouldReturn404WhenUserNotFound() {
        webTestClient().get()
                .uri("/users/{id}", 999)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldGetExternalNotificationsWithWireMock() {
        User user = userRepository.save(UserBuilder.aUser().build());

        String postsJson = ExternalPostResponseBuilder.aPost()
                .withUserId(user.getId())
                .buildListJson(2);
        WireMockHelper.stubGetPostsByUser(WIREMOCK, user.getId(), postsJson);

        webTestClient().get()
                .uri("/users/{id}/external-notifications", user.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].userId").isEqualTo(user.getId().intValue());
    }

    @Test
    void shouldReturnEmptyListWhenNoExternalPosts() {
        User user = userRepository.save(UserBuilder.aUser().build());

        WireMockHelper.stubGetPostsByUserEmpty(WIREMOCK, user.getId());

        webTestClient().get()
                .uri("/users/{id}/external-notifications", user.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void shouldFailValidationWithBlankUsername() {
        UserRequest invalid = new UserRequest("", "valid@test.com");

        webTestClient().post()
                .uri("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldDeleteUser() {
        User user = userRepository.save(UserBuilder.aUser().build());

        webTestClient().delete()
                .uri("/users/{id}", user.getId())
                .exchange()
                .expectStatus().isNoContent();

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}
