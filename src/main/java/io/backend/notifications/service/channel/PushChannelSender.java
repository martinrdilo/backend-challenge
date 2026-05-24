package io.backend.notifications.service.channel;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends notifications via push notification. Formats a JSON payload with title and content. */
@Component
public class PushChannelSender implements ChannelSender {

  private static final Logger log = LoggerFactory.getLogger(PushChannelSender.class);

  @Override
  public void send(Notification notification) {
    String deviceToken = notification.getUser().getDeviceToken();
    if (deviceToken == null) {
      throw new IllegalStateException("No device token");
    }
    String title = notification.getTitle();
    String content = notification.getContent();
    String payload = String.format("{\"title\":\"%s\",\"content\":\"%s\"}", title, content);
    log.info("Push notification dispatched to device {}: {}", deviceToken, payload);
  }

  @Override
  public Channel getChannel() {
    return Channel.PUSH;
  }
}
