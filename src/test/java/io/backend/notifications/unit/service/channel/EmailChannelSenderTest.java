package io.backend.notifications.unit.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.service.channel.EmailChannelSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link EmailChannelSender}.
 *
 * <p>Tests R2 from spec: - Rejects null user email with IllegalStateException - Logs "Email sent to
 * {email}" on success
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailChannelSender")
class EmailChannelSenderTest {

  @InjectMocks private EmailChannelSender emailChannelSender;

  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    Logger logger = (Logger) LoggerFactory.getLogger(EmailChannelSender.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @Test
  @DisplayName("should throw IllegalStateException when user email is null")
  void shouldThrowWhenUserEmailIsNull() {
    User user = mock(User.class);
    when(user.getEmail()).thenReturn(null);

    Notification notification = mock(Notification.class);
    when(notification.getUser()).thenReturn(user);

    assertThatIllegalStateException()
        .isThrownBy(() -> emailChannelSender.send(notification))
        .withMessage("User email is null");
  }

  @Test
  @DisplayName("should log email sent message when email is valid")
  void shouldLogEmailSentWhenEmailIsValid() {
    User user = mock(User.class);
    when(user.getEmail()).thenReturn("user@example.com");

    Notification notification = mock(Notification.class);
    when(notification.getUser()).thenReturn(user);

    emailChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage()).isEqualTo("Email sent to user@example.com");
            });
  }
}
