package io.backend.notifications.listener;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Status;
import io.backend.notifications.event.NotificationCreatedEvent;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.service.channel.ChannelDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 *
 * <p>Records Micrometer metrics: counter for dispatched/failed outcomes,
 * retry counter, and dispatch duration timer.
 */
@Component
public class NotificationDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchListener.class);

    private final NotificationRepository notificationRepository;
    private final ChannelDispatcher channelDispatcher;
    private final MeterRegistry meterRegistry;

    public NotificationDispatchListener(NotificationRepository notificationRepository,
                                        ChannelDispatcher channelDispatcher,
                                        MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.channelDispatcher = channelDispatcher;
        this.meterRegistry = meterRegistry;
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
        String channel = notification.getChannel() != null ? notification.getChannel().name() : "UNKNOWN";

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            channelDispatcher.dispatch(notification);
            notification.setStatus(Status.SENT);
            log.info("Notification {} dispatched successfully", event.notificationId());
            meterRegistry.counter("notification.dispatched",
                    "channel", channel,
                    "outcome", "sent").increment();
        } catch (Exception e) {
            log.error("Dispatch failed for notification {}: {}", event.notificationId(), e.getMessage());
            notification.setStatus(Status.FAILED);
            meterRegistry.counter("notification.dispatched",
                    "channel", channel,
                    "outcome", "failed").increment();
            meterRegistry.counter("notification.retry.total").increment();
        } finally {
            sample.stop(Timer.builder("notification.dispatch.duration")
                    .register(meterRegistry));
        }
        notificationRepository.save(notification);
    }
}
