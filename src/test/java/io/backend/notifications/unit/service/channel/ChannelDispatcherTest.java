package io.backend.notifications.unit.service.channel;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.*;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.service.channel.ChannelDispatcher;
import io.backend.notifications.service.channel.ChannelSender;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ChannelDispatcher}.
 *
 * <p>Tests R5 from spec: - Dispatcher resolves correct sender by channel - Unregistered channel
 * throws IllegalStateException
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelDispatcher")
class ChannelDispatcherTest {

  @Mock private ChannelSender emailSender;

  @Mock private ChannelSender smsSender;

  @Test
  @DisplayName("should invoke SMS sender for SMS channel and not email sender")
  void shouldInvokeSmsSenderForSmsChannel() {
    when(emailSender.getChannel()).thenReturn(Channel.EMAIL);
    when(smsSender.getChannel()).thenReturn(Channel.SMS);

    // Create dispatcher with configured mocks
    ChannelDispatcher channelDispatcher = new ChannelDispatcher(List.of(emailSender, smsSender));

    Notification notification = mock(Notification.class);
    when(notification.getChannel()).thenReturn(Channel.SMS);

    channelDispatcher.dispatch(notification);

    verify(smsSender).send(notification);
    verify(emailSender, never()).send(notification);
  }

  @Test
  @DisplayName("should throw IllegalStateException when no sender is registered for channel")
  void shouldThrowWhenNoSenderRegistered() {
    when(emailSender.getChannel()).thenReturn(Channel.EMAIL);
    when(smsSender.getChannel()).thenReturn(Channel.SMS);

    ChannelDispatcher channelDispatcher = new ChannelDispatcher(List.of(emailSender, smsSender));

    Notification notification = mock(Notification.class);
    when(notification.getChannel()).thenReturn(Channel.PUSH);

    assertThatIllegalStateException()
        .isThrownBy(() -> channelDispatcher.dispatch(notification))
        .withMessage("No sender registered for channel: PUSH");
  }
}
