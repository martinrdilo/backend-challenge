package io.backend.notifications.service.channel;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends notifications via email. Validates that the associated user has a non-null email address.
 */
@Component
public class EmailChannelSender implements ChannelSender {

  private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

  @Override
  public void send(Notification notification) {
    String email = notification.getUser().getEmail();
    if (email == null) {
      throw new IllegalStateException("User email is null");
    }
    log.info("Email sent to {}", email);
  }

  @Override
  public Channel getChannel() {
    return Channel.EMAIL;
  }
}
