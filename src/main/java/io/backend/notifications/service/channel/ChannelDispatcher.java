package io.backend.notifications.service.channel;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Dispatches notifications to the appropriate {@link ChannelSender} based on the notification's
 * {@link Channel}.
 *
 * <p>Collects all {@link ChannelSender} beans via Spring constructor injection and builds a lookup
 * map keyed by {@link Channel}.
 *
 * <h3>Retry semantics</h3>
 *
 * The {@link #dispatch(Notification)} method is annotated with {@link Retryable @Retryable}.
 * Transient failures (e.g. network timeouts, external API errors) are retried with exponential
 * backoff configured via {@code notifications.dispatch.retry.*} properties:
 *
 * <ul>
 *   <li>Up to {@code max-attempts} retry attempts (default 3)
 *   <li>Base delay of {@code delay-ms} (default 1000 ms)
 *   <li>Exponential multiplier of {@code multiplier} (default 2.0)
 * </ul>
 *
 * <p>{@link IllegalStateException} is excluded from retry because it represents non-transient
 * failures: missing sender registration ({@code "No sender registered for channel"}), missing user
 * data ({@code "User email is null"}, {@code "No device token"}). These failures propagate
 * immediately to {@code NotificationService}, which marks the notification as {@code FAILED}.
 *
 * <p><b>AOP note:</b> {@code @Retryable} relies on Spring AOP proxies. {@code dispatch()} must be
 * invoked on an injected bean reference (cross-bean call), never via intra-class {@code
 * this.dispatch()}. The existing call from {@code NotificationService.createNotification()}
 * satisfies this requirement with zero changes.
 */
@Service
public class ChannelDispatcher {

  private final Map<Channel, ChannelSender> senderMap;

  public ChannelDispatcher(List<ChannelSender> senders) {
    this.senderMap =
        senders.stream().collect(Collectors.toMap(ChannelSender::getChannel, Function.identity()));
  }

  /**
   * Dispatch a notification to the sender registered for its channel.
   *
   * <p>Automatically retries on transient exceptions (up to {@code
   * notifications.dispatch.retry.max-attempts} times, default 3) with exponential backoff. {@link
   * IllegalStateException} is excluded from retry and propagates immediately.
   *
   * @param notification the notification to dispatch
   * @throws IllegalStateException if no sender is registered for the notification's channel
   */
  @Retryable(
      retryFor = Exception.class,
      exclude = IllegalStateException.class,
      maxAttemptsExpression = "${notifications.dispatch.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${notifications.dispatch.retry.delay-ms:1000}",
              multiplierExpression = "${notifications.dispatch.retry.multiplier:2.0}"))
  public void dispatch(Notification notification) {
    ChannelSender sender = senderMap.get(notification.getChannel());
    if (sender == null) {
      throw new IllegalStateException(
          "No sender registered for channel: " + notification.getChannel());
    }
    sender.send(notification);
  }
}
