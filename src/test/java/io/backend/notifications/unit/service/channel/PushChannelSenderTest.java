package io.backend.notifications.unit.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.service.channel.PushChannelSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link PushChannelSender}.
 *
 * <p>Tests from spec: device token validation, dispatch logging, and payload format regression.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PushChannelSender")
class PushChannelSenderTest {

  @InjectMocks private PushChannelSender pushChannelSender;

  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    Logger logger = (Logger) LoggerFactory.getLogger(PushChannelSender.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  // ──── New tests: token validation ────

  @Test
  @DisplayName("should dispatch with valid device token and log token prefix")
  void shouldDispatchWithValidToken() {
    User user = mock(User.class);
    when(user.getDeviceToken()).thenReturn("tok_abc123");

    Notification notification = mock(Notification.class);
    when(notification.getTitle()).thenReturn("Alert");
    when(notification.getContent()).thenReturn("Server updated");
    when(notification.getUser()).thenReturn(user);

    pushChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .contains("Push notification dispatched to device tok_abc123")
                  .contains("{\"title\":\"Alert\",\"content\":\"Server updated\"}");
            });
  }

  @Test
  @DisplayName("should throw IllegalStateException when device token is null")
  void shouldThrowWhenTokenNull() {
    User user = mock(User.class);
    when(user.getDeviceToken()).thenReturn(null);

    Notification notification = mock(Notification.class);
    when(notification.getUser()).thenReturn(user);

    assertThatThrownBy(() -> pushChannelSender.send(notification))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No device token");
  }

  @Test
  @DisplayName("should preserve JSON payload formatting")
  void shouldPreservePayloadFormatting() {
    User user = mock(User.class);
    when(user.getDeviceToken()).thenReturn("tok_xyz");

    Notification notification = mock(Notification.class);
    when(notification.getTitle()).thenReturn("Test");
    when(notification.getContent()).thenReturn("Message with quotes");
    when(notification.getUser()).thenReturn(user);

    pushChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage())
                  .contains("Push notification dispatched to device tok_xyz")
                  .contains("{\"title\":\"Test\",\"content\":\"Message with quotes\"}");
            });
  }

  // ──── Updated existing test ────

  @Test
  @DisplayName("should log push notification with JSON payload containing title and content")
  void shouldLogPushNotificationWithJsonPayload() {
    User user = mock(User.class);
    when(user.getDeviceToken()).thenReturn("tok_dev");

    Notification notification = mock(Notification.class);
    when(notification.getTitle()).thenReturn("Alert");
    when(notification.getContent()).thenReturn("Server updated");
    when(notification.getUser()).thenReturn(user);

    pushChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .contains("Push notification dispatched")
                  .contains("{\"title\":\"Alert\",\"content\":\"Server updated\"}");
            });
  }
}
