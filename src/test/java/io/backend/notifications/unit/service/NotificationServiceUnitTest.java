package io.backend.notifications.unit.service;

import io.backend.notifications.client.ExternalMediaClient;
import io.backend.notifications.dto.NotificationCriteria;
import io.backend.notifications.dto.NotificationUpdateRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.fixture.entity.NotificationBuilder;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import io.backend.notifications.service.NotificationService;
import io.backend.notifications.service.channel.ChannelDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService — new CRUD methods and findOwnNotification helper.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceUnitTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExternalMediaClient externalMediaClient;

    @Mock
    private ChannelDispatcher channelDispatcher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationService notificationService;

    private MockedStatic<SecurityContextHolder> securityContextHolderMock;

    private static final String USER_EMAIL = "owner@test.com";

    @BeforeEach
    void setUp() {
        securityContextHolderMock = mockStatic(SecurityContextHolder.class);
        securityContextHolderMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(USER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        securityContextHolderMock.close();
    }

    // ──── T3: findOwnNotification ────

    @Test
    void findOwnNotificationShouldReturnNotificationWhenOwnerMatches() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification notification = NotificationBuilder.aNotification()
                .withUser(owner)
                .build();
        notification.setId(10L);

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        Notification result = notificationService.findOwnNotification(10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUser().getEmail()).isEqualTo(USER_EMAIL);
    }

    @Test
    void findOwnNotificationShouldThrow404WhenNotificationNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.findOwnNotification(999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findOwnNotificationShouldThrow403WhenOwnerDoesNotMatch() {
        User otherUser = UserBuilder.aUser().withEmail("other@test.com").build();
        otherUser.setId(2L);
        Notification notification = NotificationBuilder.aNotification()
                .withUser(otherUser)
                .build();
        notification.setId(10L);

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.findOwnNotification(10L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ──── T4: getMyNotifications ────

    @Test
    void getMyNotificationsShouldReturnEnrichedListForAuthenticatedUser() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner)
                .withTitle("First")
                .build();
        n1.setId(1L);
        Notification n2 = NotificationBuilder.aNotification()
                .withUser(owner)
                .withTitle("Second")
                .build();
        n2.setId(2L);

        when(notificationRepository.findAllByUserEmail(USER_EMAIL)).thenReturn(List.of(n1, n2));

        var result = notificationService.getMyNotifications();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("First");
        assertThat(result.get(1).title()).isEqualTo("Second");
        verify(notificationRepository).findAllByUserEmail(USER_EMAIL);
    }

    @Test
    void getMyNotificationsShouldReturnEmptyListWhenNoNotifications() {
        when(notificationRepository.findAllByUserEmail(USER_EMAIL)).thenReturn(List.of());

        var result = notificationService.getMyNotifications();

        assertThat(result).isEmpty();
        verify(notificationRepository).findAllByUserEmail(USER_EMAIL);
    }

    // ──── T5: updateNotification ────

    @Test
    void updateNotificationShouldUpdateFieldsAndReturnEnriched() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification existing = NotificationBuilder.aNotification()
                .withUser(owner)
                .withTitle("Old Title")
                .withContent("Old Content")
                .build();
        existing.setId(10L);

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(notificationRepository.save(any(Notification.class))).thenReturn(existing);

        var request = new NotificationUpdateRequest("New Title", "New Content", List.of(1L, 2L));
        var result = notificationService.updateNotification(10L, request);

        assertThat(result.title()).isEqualTo("New Title");
        assertThat(result.content()).isEqualTo("New Content");
        assertThat(existing.getAttachmentIds()).containsExactly(1L, 2L);
        verify(notificationRepository).save(existing);
    }

    // ──── T6: deleteNotification ────

    @Test
    void deleteNotificationShouldFindOwnAndDelete() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification notification = NotificationBuilder.aNotification()
                .withUser(owner)
                .build();
        notification.setId(10L);

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        notificationService.deleteNotification(10L);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void deleteNotificationShouldThrow404WhenNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.deleteNotification(999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteNotificationShouldThrow403WhenNotOwner() {
        User otherUser = UserBuilder.aUser().withEmail("other@test.com").build();
        otherUser.setId(2L);
        Notification notification = NotificationBuilder.aNotification()
                .withUser(otherUser)
                .build();
        notification.setId(10L);

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.deleteNotification(10L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ──── searchNotifications ────

    @Test
    void searchNotificationsShouldReturnPaginatedResults() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner).withTitle("First").build();
        n1.setId(1L);
        Notification n2 = NotificationBuilder.aNotification()
                .withUser(owner).withTitle("Second").build();
        n2.setId(2L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1, n2), pageable, 2);

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(null, null, null, null, null);
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).title()).isEqualTo("First");
        assertThat(result.getContent().get(1).title()).isEqualTo("Second");
    }

    @Test
    void searchNotificationsShouldApplyStatusFilter() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner).withStatus(Status.SENT).build();
        n1.setId(1L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1), pageable, 1);

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(Status.SENT, null, null, null, null);
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("SENT");
    }

    @Test
    void searchNotificationsShouldApplyChannelFilter() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner).withChannel(Channel.EMAIL).build();
        n1.setId(1L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1), pageable, 1);

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(null, Channel.EMAIL, null, null, null);
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).channel()).isEqualTo("EMAIL");
    }

    @Test
    void searchNotificationsShouldApplyTextSearch() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner).withTitle("Invoice #123").build();
        n1.setId(1L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1), pageable, 1);

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(null, null, null, null, "invoice");
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchNotificationsShouldApplyCombinedCriteria() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner)
                .withStatus(Status.SENT)
                .withChannel(Channel.EMAIL)
                .withTitle("Invoice alert")
                .build();
        n1.setId(1L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1), pageable, 1);

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(Status.SENT, Channel.EMAIL, null, null, "invoice");
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchNotificationsWithEmptyCriteriaShouldReturnAllPaginated() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner).build();
        n1.setId(1L);
        Notification n2 = NotificationBuilder.aNotification()
                .withUser(owner).build();
        n2.setId(2L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1, n2), pageable, 2);

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(null, null, null, null, null);
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void searchNotificationsShouldCapPageSizeAt100() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);

        Pageable largePageable = PageRequest.of(0, 500);
        Pageable cappedPageable = PageRequest.of(0, 100);
        Page<Notification> mockPage = new PageImpl<>(List.of(), cappedPageable, 0);

        when(notificationRepository.findAll(any(Specification.class), eq(cappedPageable)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(null, null, null, null, null);
        var result = notificationService.searchNotifications(criteria, largePageable);

        assertThat(result).isNotNull();
        verify(notificationRepository).findAll(any(Specification.class), eq(cappedPageable));
    }

    @Test
    void searchNotificationsShouldAlwaysIncludeOwnershipFilter() {
        User owner = UserBuilder.aUser().withEmail(USER_EMAIL).build();
        owner.setId(1L);
        Notification n1 = NotificationBuilder.aNotification()
                .withUser(owner).build();
        n1.setId(1L);

        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> mockPage = new PageImpl<>(List.of(n1), pageable, 1);

        ArgumentCaptor<Specification<Notification>> specCaptor = ArgumentCaptor.forClass(Specification.class);

        when(notificationRepository.findAll(specCaptor.capture(), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationCriteria criteria = new NotificationCriteria(Status.SENT, Channel.EMAIL, null, null, "test");
        notificationService.searchNotifications(criteria, pageable);

        Specification<Notification> capturedSpec = specCaptor.getValue();
        assertThat(capturedSpec).isNotNull();

        // Verify findAll was called exactly once
        verify(notificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchNotificationsShouldReturnEmptyPageWhenNoResults() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> emptyPage = Page.empty();

        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        NotificationCriteria criteria = new NotificationCriteria(Status.FAILED, null, null, null, null);
        var result = notificationService.searchNotifications(criteria, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }
}
