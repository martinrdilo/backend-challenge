package io.backend.notifications.unit.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Status;
import io.backend.notifications.event.NotificationCreatedEvent;
import io.backend.notifications.listener.NotificationDispatchListener;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.service.channel.ChannelDispatcher;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link NotificationDispatchListener}.
 *
 * <p>Verifies the listener loads the notification, dispatches via {@link ChannelDispatcher}, and
 * updates status to SENT or FAILED.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchListener")
class NotificationDispatchListenerTest {

  @Mock private NotificationRepository notificationRepository;

  @Mock private ChannelDispatcher channelDispatcher;

  @InjectMocks private NotificationDispatchListener listener;

  @Test
  @DisplayName("should dispatch notification and update status to SENT")
  void shouldDispatchNotificationAndUpdateStatusToSent() {
    Notification notification = new Notification();
    notification.setId(1L);
    notification.setStatus(Status.PENDING);

    when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

    listener.onNotificationCreated(new NotificationCreatedEvent(1L));

    verify(channelDispatcher).dispatch(notification);
    assertThat(notification.getStatus()).isEqualTo(Status.SENT);
    verify(notificationRepository).save(notification);
  }

  @Test
  @DisplayName("should set status to FAILED when dispatch throws")
  void shouldSetFailedStatusWhenDispatchThrows() {
    Notification notification = new Notification();
    notification.setId(1L);
    notification.setStatus(Status.PENDING);

    when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
    doThrow(new RuntimeException("dispatch failed")).when(channelDispatcher).dispatch(any());

    listener.onNotificationCreated(new NotificationCreatedEvent(1L));

    assertThat(notification.getStatus()).isEqualTo(Status.FAILED);
    verify(notificationRepository).save(notification);
  }

  @Test
  @DisplayName("should do nothing when notification is not found")
  void shouldDoNothingWhenNotificationNotFound() {
    when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

    listener.onNotificationCreated(new NotificationCreatedEvent(99L));

    verify(channelDispatcher, never()).dispatch(any());
    verify(notificationRepository, never()).save(any());
  }
}
