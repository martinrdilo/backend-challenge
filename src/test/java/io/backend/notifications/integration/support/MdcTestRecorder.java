package io.backend.notifications.integration.support;

import io.backend.notifications.event.NotificationCreatedEvent;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only component that captures MDC context during async event processing.
 *
 * <p>Listens for the same {@link NotificationCreatedEvent} on the same
 * async executor as the production {@code NotificationDispatchListener}.
 * Because the {@code TaskDecorator} copies MDC from the submitter thread,
 * the captured context proves that correlation IDs survive the async boundary.
 *
 * <p>Only active in the {@code test} profile — never loaded in production.
 */
@Component
@Profile("test")
public class MdcTestRecorder {

    private final ConcurrentHashMap<Long, Map<String, String>> captures = new ConcurrentHashMap<>();

    @Async("notificationDispatchExecutor")
    @EventListener
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        if (ctx != null) {
            captures.put(event.notificationId(), Map.copyOf(ctx));
        }
    }

    /**
     * Returns the MDC context captured during async processing for the given notification,
     * or {@code null} if the event has not been processed yet.
     */
    public Map<String, String> getMdcContext(Long notificationId) {
        return captures.get(notificationId);
    }

    /**
     * Clears all captured contexts.
     */
    public void clear() {
        captures.clear();
    }
}
