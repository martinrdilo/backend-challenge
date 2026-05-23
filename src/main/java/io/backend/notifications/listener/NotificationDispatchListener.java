package io.backend.notifications.listener;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Status;
import io.backend.notifications.event.NotificationCreatedEvent;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.service.channel.ChannelDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles {@link NotificationCreatedEvent} asynchronously on a dedicated
 * thread pool, dispatching the notification via {@link ChannelDispatcher}
 * in its own transaction.
 *
 * <p>Runs in a new transaction ({@code REQUIRES_NEW}) so the producer's
 * commit is always independent from the listener's work. If the dispatch
 * fails, the notification status is set to {@code FAILED}.
 */
@Component
public class NotificationDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchListener.class);

    private final NotificationRepository notificationRepository;
    private final ChannelDispatcher channelDispatcher;

    public NotificationDispatchListener(NotificationRepository notificationRepository,
                                        ChannelDispatcher channelDispatcher) {
        this.notificationRepository = notificationRepository;
        this.channelDispatcher = channelDispatcher;
    }

    @Async("notificationDispatchExecutor")
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Optional<Notification> optionalNotification = notificationRepository.findById(event.notificationId());

        if (optionalNotification.isEmpty()) {
            log.warn("Notification {} not found — event will be ignored", event.notificationId());
            return;
        }

        Notification notification = optionalNotification.get();
        try {
            channelDispatcher.dispatch(notification);
            notification.setStatus(Status.SENT);
            log.info("Notification {} dispatched successfully", event.notificationId());
        } catch (Exception e) {
            log.error("Dispatch failed for notification {}: {}", event.notificationId(), e.getMessage());
            notification.setStatus(Status.FAILED);
        }
        notificationRepository.save(notification);
    }
}
