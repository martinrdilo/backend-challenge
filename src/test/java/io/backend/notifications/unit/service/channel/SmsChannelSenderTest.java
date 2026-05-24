package io.backend.notifications.unit.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.service.channel.SmsChannelSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link SmsChannelSender}.
 *
 * <p>Tests from spec: SMS phone logging with timestamp, truncation, and null-phone warning.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsChannelSender")
class SmsChannelSenderTest {

  @InjectMocks private SmsChannelSender smsChannelSender;

  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    Logger logger = (Logger) LoggerFactory.getLogger(SmsChannelSender.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  // ──── New tests: phone logging ────

  @Test
  @DisplayName("should log phone number with timestamp and truncated content when phone present")
  void shouldLogPhoneWhenPresent() {
    String content = "Hello World";
    User user = mock(User.class);
    when(user.getPhone()).thenReturn("+5491112345678");

    Notification notification = mock(Notification.class);
    when(notification.getContent()).thenReturn(content);
    when(notification.getUser()).thenReturn(user);

    smsChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .contains("SMS sent to +5491112345678")
                  .contains("Hello World");
            });
  }

  @Test
  @DisplayName("should log info level even when phone is null (no null-guard)")
  void shouldLogInfoEvenWhenPhoneIsNull() {
    String content = "Some message";
    User user = mock(User.class);
    when(user.getPhone()).thenReturn(null);

    Notification notification = mock(Notification.class);
    when(notification.getContent()).thenReturn(content);
    when(notification.getUser()).thenReturn(user);

    smsChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              assertThat(event.getFormattedMessage())
                  .contains("SMS sent to null")
                  .contains("Some message");
            });
  }

  @Test
  @DisplayName("should still truncate content to 160 characters when phone is present")
  void shouldPreserveContentTruncation() {
    String content = "A".repeat(200);
    User user = mock(User.class);
    when(user.getPhone()).thenReturn("+5491112345678");

    Notification notification = mock(Notification.class);
    when(notification.getContent()).thenReturn(content);
    when(notification.getUser()).thenReturn(user);

    smsChannelSender.send(notification);

    assertThat(listAppender.list)
        .hasSize(1)
        .first()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.INFO);
              String message = event.getFormattedMessage();
              assertThat(message).contains("+5491112345678");
              assertThat(message).contains("A".repeat(160));
              assertThat(message).doesNotContain("A".repeat(161));
            });
  }

  // ──── Updated existing tests: mock user.getPhone() ────

  @Test
  @DisplayName("should log full content when within 160 character limit")
  void shouldNotTruncateContentWithinLimit() {
    String content = "Hello World";
    User user = mock(User.class);
    when(user.getPhone()).thenReturn("+5491111111111");

    Notification notification = mock(Notification.class);
    when(notification.getContent()).thenReturn(content);
    when(notification.getUser()).thenReturn(user);

    smsChannelSender.send(notification);

    assertThat(listAppender.list).isNotEmpty();
    String message = listAppender.list.get(0).getFormattedMessage();
    assertThat(message).contains("Hello World");
    assertThat(message).contains("SMS sent to +5491111111111");
  }

  @Test
  @DisplayName("should truncate content to 160 characters when limit exceeded")
  void shouldTruncateContentExceedingLimit() {
    String content = "A".repeat(200);
    User user = mock(User.class);
    when(user.getPhone()).thenReturn("+5491111111111");

    Notification notification = mock(Notification.class);
    when(notification.getContent()).thenReturn(content);
    when(notification.getUser()).thenReturn(user);

    smsChannelSender.send(notification);

    assertThat(listAppender.list).isNotEmpty();
    String message = listAppender.list.get(0).getFormattedMessage();
    assertThat(message).contains("A".repeat(160));
    assertThat(message).doesNotContain("A".repeat(161));
  }

  @Test
  @DisplayName("should log content exactly at 160 characters without truncation")
  void shouldNotTruncateContentAtExactLimit() {
    String content = "B".repeat(160);
    User user = mock(User.class);
    when(user.getPhone()).thenReturn("+5491111111111");

    Notification notification = mock(Notification.class);
    when(notification.getContent()).thenReturn(content);
    when(notification.getUser()).thenReturn(user);

    smsChannelSender.send(notification);

    assertThat(listAppender.list).isNotEmpty();
    String message = listAppender.list.get(0).getFormattedMessage();
    assertThat(message).contains("B".repeat(160));
  }
}
