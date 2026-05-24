package io.backend.notifications.service.channel;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;

/**
 * Strategy interface for channel-specific notification sending. Implementations are auto-discovered
 * by Spring via @Component and self-register into {@link ChannelDispatcher} via {@link
 * #getChannel()}.
 */
public interface ChannelSender {

  /**
   * Send a notification through this channel.
   *
   * @param notification the notification to send
   */
  void send(Notification notification);

  /**
   * Returns the Channel enum value this sender handles. Used by {@link ChannelDispatcher} to build
   * the sender map.
   *
   * @return the channel this sender is responsible for
   */
  Channel getChannel();
}
