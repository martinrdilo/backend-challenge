package io.backend.notifications.integration.controller;

import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.dto.NotificationUpdateRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.fixture.entity.NotificationBuilder;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.fixture.wiremock.WireMockHelper;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * Integration tests for NotificationController with JWT authentication.
 * Tests CRUD: create, list own, get by id (with ownership), update, delete.
 */
class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    // ──── POST (existing) ────

    @Test
    void shouldCreateNotificationWhenAuthenticated() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest(
                "Test Title",
                "Test Content",
                Channel.EMAIL,
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Test Title")
                .jsonPath("$.channel").isEqualTo("EMAIL");
    }

    @Test
    void shouldReturn401WhenCreatingNotificationWithoutToken() {
        NotificationRequest request = new NotificationRequest(
                "Test Title",
                "Test Content",
                Channel.EMAIL,
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldSetStatusToSentForEmailChannel() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest(
                "Test Title",
                "Test Content",
                Channel.EMAIL,
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SENT");
    }

    @Test
    void shouldSetStatusToSentForSmsChannelWithLongContent() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        String longContent = "A".repeat(200);
        NotificationRequest request = new NotificationRequest(
                "SMS Notification",
                longContent,
                Channel.SMS,
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SENT");
    }

    @Test
    void shouldSetStatusToSentForPushChannel() {
        UserBuilder builder = UserBuilder.aUser().withDeviceToken("tok_test");
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest(
                "Push Notification",
                "Push message content",
                Channel.PUSH,
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SENT");
    }

    // ──── T7: GET /notifications (list own) ────

    @Test
    void shouldListOwnNotifications() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create 2 notifications
        NotificationRequest r1 = new NotificationRequest("First", "Content 1", Channel.EMAIL, List.of());
        NotificationRequest r2 = new NotificationRequest("Second", "Content 2", Channel.SMS, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(r1)
                .exchange()
                .expectStatus().isCreated();

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(r2)
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].title").isEqualTo("Second")
                .jsonPath("$.content[1].title").isEqualTo("First")
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyListWhenNoNotifications() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        webTestClient().get()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void shouldReturn401WhenListingNotificationsWithoutToken() {
        webTestClient().get()
                .uri("/notifications")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ──── T8: Fix GET /notifications/{id} — ownership enforcement ────

    @Test
    void shouldReturn403WhenGettingAnotherUsersNotification() {
        // User A
        UserBuilder builderA = UserBuilder.aUser();
        String tokenA = registerAndLogin(builderA);

        // User B — create a notification owned by B
        UserBuilder builderB = UserBuilder.aUser();
        String tokenB = registerAndLogin(builderB);

        NotificationRequest req = new NotificationRequest("B's Notification", "Secret", Channel.EMAIL, List.of());
        var result = webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        // Parse the created notification id from the response (or query DB)
        var userB = userRepository.findByEmail(builderB.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(userB.getId());
        Long notificationId = notifications.get(0).getId();

        // User A tries to read User B's notification
        webTestClient().get()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ──── T9: PUT /notifications/{id} ────

    @Test
    void shouldUpdateOwnNotification() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification first
        NotificationRequest createReq = new NotificationRequest("Old Title", "Old Content", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isCreated();

        var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(user.getId());
        Long notificationId = notifications.get(0).getId();

        // Stub photo resolution
        WireMockHelper.stubGetPhoto(WIREMOCK, 1L, """
                {"albumId":1,"id":1,"title":"photo1","url":"https://example.com/1","thumbnailUrl":"https://example.com/t1"}""");
        WireMockHelper.stubGetPhoto(WIREMOCK, 2L, """
                {"albumId":1,"id":2,"title":"photo2","url":"https://example.com/2","thumbnailUrl":"https://example.com/t2"}""");

        NotificationUpdateRequest updateReq = new NotificationUpdateRequest("New Title", "New Content", List.of(1L, 2L));

        webTestClient().put()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("New Title")
                .jsonPath("$.content").isEqualTo("New Content")
                .jsonPath("$.attachments").isArray()
                .jsonPath("$.attachments.length()").isEqualTo(2)
                .jsonPath("$.attachments[0].id").isEqualTo(1)
                .jsonPath("$.attachments[1].id").isEqualTo(2);
    }

    @Test
    void shouldClearAttachmentsOnUpdate() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification with attachment IDs
        NotificationRequest createReq = new NotificationRequest("With Attachments", "Content", Channel.EMAIL, List.of(1L));
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isCreated();

        var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(user.getId());
        Long notificationId = notifications.get(0).getId();

        // Update with null attachmentIds — clears them
        NotificationUpdateRequest updateReq = new NotificationUpdateRequest("Updated", "New Content", null);

        webTestClient().put()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Updated")
                .jsonPath("$.attachments").isArray()
                .jsonPath("$.attachments.length()").isEqualTo(0);
    }

    @Test
    void shouldReturn403WhenUpdatingAnotherUsersNotification() {
        UserBuilder builderA = UserBuilder.aUser();
        String tokenA = registerAndLogin(builderA);

        UserBuilder builderB = UserBuilder.aUser();
        String tokenB = registerAndLogin(builderB);

        // User B creates a notification
        NotificationRequest req = new NotificationRequest("B's", "Secret", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();

        var userB = userRepository.findByEmail(builderB.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(userB.getId());
        Long notificationId = notifications.get(0).getId();

        // User A tries to update User B's notification
        NotificationUpdateRequest updateReq = new NotificationUpdateRequest("Hacked", "Evil", List.of());
        webTestClient().put()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentNotification() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationUpdateRequest updateReq = new NotificationUpdateRequest("Title", "Content", List.of());
        webTestClient().put()
                .uri("/notifications/{id}", 99999L)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturn401WhenUpdatingWithoutToken() {
        NotificationUpdateRequest updateReq = new NotificationUpdateRequest("Title", "Content", List.of());
        webTestClient().put()
                .uri("/notifications/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn400WhenUpdatingWithBlankTitle() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification first
        NotificationRequest createReq = new NotificationRequest("Valid", "Content", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isCreated();

        var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(user.getId());
        Long notificationId = notifications.get(0).getId();

        // Update with blank title
        NotificationUpdateRequest updateReq = new NotificationUpdateRequest(" ", "Content", List.of());
        webTestClient().put()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenUpdatingWithBlankContent() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification first
        NotificationRequest createReq = new NotificationRequest("Valid", "Content", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isCreated();

        var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(user.getId());
        Long notificationId = notifications.get(0).getId();

        // Update with blank content
        NotificationUpdateRequest updateReq = new NotificationUpdateRequest("Title", " ", List.of());
        webTestClient().put()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateReq)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ──── T10: DELETE /notifications/{id} ────

    @Test
    void shouldDeleteOwnNotification() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification
        NotificationRequest createReq = new NotificationRequest("To Delete", "Bye", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createReq)
                .exchange()
                .expectStatus().isCreated();

        var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(user.getId());
        Long notificationId = notifications.get(0).getId();

        // Delete
        webTestClient().delete()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isEqualTo(204);

        // Verify it's really gone
        webTestClient().get()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentNotification() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        webTestClient().delete()
                .uri("/notifications/{id}", 99999L)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturn403WhenDeletingAnotherUsersNotification() {
        UserBuilder builderA = UserBuilder.aUser();
        String tokenA = registerAndLogin(builderA);

        UserBuilder builderB = UserBuilder.aUser();
        String tokenB = registerAndLogin(builderB);

        // User B creates a notification
        NotificationRequest req = new NotificationRequest("B's", "Secret", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();

        var userB = userRepository.findByEmail(builderB.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(userB.getId());
        Long notificationId = notifications.get(0).getId();

        // User A tries to delete User B's notification
        webTestClient().delete()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldReturn401WhenDeletingWithoutToken() {
        webTestClient().delete()
                .uri("/notifications/{id}", 1L)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ──── Legacy: updated to use new endpoints ────

    @Test
    void shouldReturn401WhenGettingNotificationsWithoutToken() {
        webTestClient().get()
                .uri("/notifications")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldGetOwnNotifications() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification first
        NotificationRequest request = new NotificationRequest(
                "My Notification",
                "My Content",
                Channel.SMS,
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        // Retrieve own notifications via new endpoint — Page envelope
        webTestClient().get()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].title").isEqualTo("My Notification")
                .jsonPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void shouldReturn403WhenAccessingAnotherUsersNotifications() {
        // User A
        UserBuilder builderA = UserBuilder.aUser();
        String tokenA = registerAndLogin(builderA);

        // User B — create notification owned by B
        UserBuilder builderB = UserBuilder.aUser();
        String tokenB = registerAndLogin(builderB);

        NotificationRequest req = new NotificationRequest("B's Notification", "Secret", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isCreated();

        var userB = userRepository.findByEmail(builderB.getEmail()).orElseThrow();
        var notifications = notificationRepository.findAllByUserId(userB.getId());
        Long notificationId = notifications.get(0).getId();

        // User A tries to GET User B's notification by ID
        webTestClient().get()
                .uri("/notifications/{id}", notificationId)
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ──── Input Validation (spec: input-validation) ────

    @Test
    void shouldReturn400WhenCreatingWithBlankTitle() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest("", "Valid Content", Channel.EMAIL, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenCreatingWithNullTitle() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest(null, "Valid Content", Channel.EMAIL, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenCreatingWithBlankContent() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest("Valid Title", "", Channel.EMAIL, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenCreatingWithWhitespaceOnlyTitle() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest("   ", "Valid Content", Channel.EMAIL, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenCreatingWithInvalidChannel() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        String json = """
                {"title":"Test","content":"Body","channel":"INVALID","attachmentIds":[]}""";

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturn400WhenCreatingWithNullChannel() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        String json = """
                {"title":"Test","content":"Body","channel":null,"attachmentIds":[]}""";

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldCreateNotificationWithSmsChannel() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest("SMS Title", "SMS Body", Channel.SMS, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.channel").isEqualTo("SMS");
    }

    @Test
    void shouldCreateNotificationWithPushChannel() {
        UserBuilder builder = UserBuilder.aUser().withDeviceToken("tok_test");
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest("Push Title", "Push Body", Channel.PUSH, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.channel").isEqualTo("PUSH");
    }

    @Test
    void shouldCreateNotificationWithEmailChannel() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest("Email Title", "Email Body", Channel.EMAIL, List.of());

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.channel").isEqualTo("EMAIL");
    }

    // ──── Pagination & Search ────

    @Test
    void shouldReturnDefaultPagination() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create 3 notifications
        for (int i = 0; i < 3; i++) {
            NotificationRequest req = new NotificationRequest("Title " + i, "Content " + i, Channel.EMAIL, List.of());
            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isCreated();
        }

        webTestClient().get()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(3)
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(1)
                .jsonPath("$.number").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20);
    }

    @Test
    void shouldFilterByStatus() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create 2 SENT notifications via API
        NotificationRequest req1 = new NotificationRequest("Sent 1", "Content", Channel.EMAIL, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req1)
                .exchange()
                .expectStatus().isCreated();

        NotificationRequest req2 = new NotificationRequest("Sent 2", "Content", Channel.SMS, List.of());
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req2)
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications?status=SENT")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].status").isEqualTo("SENT")
                .jsonPath("$.content[1].status").isEqualTo("SENT");
    }

    @Test
    void shouldFilterByChannel() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create EMAIL notification
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Email msg", "Body", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        // Create SMS notification
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("SMS msg", "Body", Channel.SMS, List.of()))
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications?channel=EMAIL")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].channel").isEqualTo("EMAIL");
    }

    @Test
    void shouldSearchByText() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create notification with searchable content
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Invoice Reminder", "Your invoice is ready", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        // Create another notification without the keyword
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Welcome", "Hello there", Channel.SMS, List.of()))
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications?search=invoice")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].title").isEqualTo("Invoice Reminder");
    }

    @Test
    void shouldApplyCombinedCriteria() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // SENT + EMAIL with keyword
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Invoice Alert", "Invoice content", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        // SENT + EMAIL without keyword
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Welcome", "Hello", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        // SENT + SMS with keyword
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Invoice SMS", "Invoice via SMS", Channel.SMS, List.of()))
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications?status=SENT&channel=EMAIL&search=invoice")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].title").isEqualTo("Invoice Alert")
                .jsonPath("$.content[0].status").isEqualTo("SENT")
                .jsonPath("$.content[0].channel").isEqualTo("EMAIL");
    }

    @Test
    void shouldReturnEmptyResultForNonMatchingCriteria() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create a notification
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Title", "Content", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications?status=FAILED")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void shouldEnforceCrossUserIsolation() {
        // User A
        UserBuilder builderA = UserBuilder.aUser();
        String tokenA = registerAndLogin(builderA);

        // User B
        UserBuilder builderB = UserBuilder.aUser();
        String tokenB = registerAndLogin(builderB);

        // User A creates notification
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("A's Notification", "A's content", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        // User B creates notification
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("B's Notification", "B's content", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        // User A searches — should only see own notifications
        webTestClient().get()
                .uri("/notifications")
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].title").isEqualTo("A's Notification");
    }

    @Test
    void shouldReturnEmptyPageBeyondLast() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create 1 notification
        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NotificationRequest("Title", "Content", Channel.EMAIL, List.of()))
                .exchange()
                .expectStatus().isCreated();

        webTestClient().get()
                .uri("/notifications?page=99")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void shouldCapPageSizeAt100() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Create 3 notifications (fewer than 100 to verify max ceiling)
        for (int i = 0; i < 3; i++) {
            webTestClient().post()
                    .uri("/notifications")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new NotificationRequest("Title " + i, "Content " + i, Channel.EMAIL, List.of()))
                    .exchange()
                    .expectStatus().isCreated();
        }

        // Request size=500 — should be capped to 100
        webTestClient().get()
                .uri("/notifications?size=500")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(100);
    }
}
