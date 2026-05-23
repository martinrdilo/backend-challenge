package io.backend.notifications.unit.listener;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.event.NotificationCreatedEvent;
import io.backend.notifications.listener.NotificationDispatchListener;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.service.channel.ChannelDispatcher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationDispatchListener}.
 *
 * <p>Verifies the listener loads the notification, dispatches via
 * {@link ChannelDispatcher}, records Micrometer metrics (counters + timer),
 * and updates status to SENT or FAILED.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchListener")
class NotificationDispatchListenerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ChannelDispatcher channelDispatcher;

    private MeterRegistry meterRegistry;
    private NotificationDispatchListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // RED PHASE: constructor with MeterRegistry does NOT exist yet
        listener = new NotificationDispatchListener(notificationRepository, channelDispatcher, meterRegistry);
    }

    // ──── Existing behavior preserved ────

    @Test
    @DisplayName("should dispatch notification and update status to SENT")
    void shouldDispatchNotificationAndUpdateStatusToSent() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setChannel(Channel.EMAIL);
        notification.setStatus(Status.PENDING);

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        listener.onNotificationCreated(new NotificationCreatedEvent(1L));

        verify(channelDispatcher).dispatch(notification);
        assertThat(notification.getStatus()).isEqualTo(Status.SENT);
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("should set status to FAILED when dispatch throws")
    void shouldSetFailedStatusWhenDispatchThrows() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setChannel(Channel.PUSH);
        notification.setStatus(Status.PENDING);

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        doThrow(new RuntimeException("dispatch failed")).when(channelDispatcher).dispatch(any());

        listener.onNotificationCreated(new NotificationCreatedEvent(1L));

        assertThat(notification.getStatus()).isEqualTo(Status.FAILED);
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("should do nothing when notification is not found")
    void shouldDoNothingWhenNotificationNotFound() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        listener.onNotificationCreated(new NotificationCreatedEvent(99L));

        verify(channelDispatcher, never()).dispatch(any());
        verify(notificationRepository, never()).save(any());
    }

    // ──── NEW: Metrics assertions ────

    @Nested
    @DisplayName("dispatch metrics")
    class DispatchMetrics {

        @Test
        @DisplayName("should increment dispatched counter with SENT outcome on success")
        void shouldIncrementDispatchedCounterWithSentOutcome() {
            Notification notification = new Notification();
            notification.setId(2L);
            notification.setChannel(Channel.SMS);
            notification.setStatus(Status.PENDING);

            when(notificationRepository.findById(2L)).thenReturn(Optional.of(notification));

            listener.onNotificationCreated(new NotificationCreatedEvent(2L));

            Counter dispatched = meterRegistry.find("notification.dispatched")
                    .tag("channel", "SMS")
                    .tag("outcome", "sent")
                    .counter();
            assertThat(dispatched).isNotNull();
            assertThat(dispatched.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment dispatched counter with FAILED outcome on exception")
        void shouldIncrementDispatchedCounterWithFailedOutcome() {
            Notification notification = new Notification();
            notification.setId(3L);
            notification.setChannel(Channel.PUSH);
            notification.setStatus(Status.PENDING);

            when(notificationRepository.findById(3L)).thenReturn(Optional.of(notification));
            doThrow(new RuntimeException("dispatch error")).when(channelDispatcher).dispatch(any());

            listener.onNotificationCreated(new NotificationCreatedEvent(3L));

            Counter dispatched = meterRegistry.find("notification.dispatched")
                    .tag("channel", "PUSH")
                    .tag("outcome", "failed")
                    .counter();
            assertThat(dispatched).isNotNull();
            assertThat(dispatched.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("retry metrics")
    class RetryMetrics {

        @Test
        @DisplayName("should increment retry counter when dispatch fails after retries")
        void shouldIncrementRetryCounterWhenDispatchFails() {
            Notification notification = new Notification();
            notification.setId(4L);
            notification.setChannel(Channel.EMAIL);
            notification.setStatus(Status.PENDING);

            when(notificationRepository.findById(4L)).thenReturn(Optional.of(notification));
            doThrow(new RuntimeException("exhausted retries")).when(channelDispatcher).dispatch(any());

            listener.onNotificationCreated(new NotificationCreatedEvent(4L));

            Counter retry = meterRegistry.find("notification.retry.total").counter();
            assertThat(retry).isNotNull();
            assertThat(retry.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should NOT increment retry counter on successful dispatch")
        void shouldNotIncrementRetryCounterOnSuccess() {
            Notification notification = new Notification();
            notification.setId(5L);
            notification.setChannel(Channel.SMS);
            notification.setStatus(Status.PENDING);

            when(notificationRepository.findById(5L)).thenReturn(Optional.of(notification));

            listener.onNotificationCreated(new NotificationCreatedEvent(5L));

            Counter retry = meterRegistry.find("notification.retry.total").counter();
            // Counter not registered if never incremented, so find returns null
            assertThat(retry).isNull();
        }
    }

    @Nested
    @DisplayName("dispatch timer")
    class DispatchTimer {

        @Test
        @DisplayName("should record dispatch duration on success")
        void shouldRecordDispatchDurationOnSuccess() {
            Notification notification = new Notification();
            notification.setId(6L);
            notification.setChannel(Channel.EMAIL);
            notification.setStatus(Status.PENDING);

            when(notificationRepository.findById(6L)).thenReturn(Optional.of(notification));

            listener.onNotificationCreated(new NotificationCreatedEvent(6L));

            Timer timer = meterRegistry.find("notification.dispatch.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should record dispatch duration on failure")
        void shouldRecordDispatchDurationOnFailure() {
            Notification notification = new Notification();
            notification.setId(7L);
            notification.setChannel(Channel.PUSH);
            notification.setStatus(Status.PENDING);

            when(notificationRepository.findById(7L)).thenReturn(Optional.of(notification));
            doThrow(new RuntimeException("timed failure")).when(channelDispatcher).dispatch(any());

            listener.onNotificationCreated(new NotificationCreatedEvent(7L));

            Timer timer = meterRegistry.find("notification.dispatch.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("no metrics when notification not found")
    class NoMetricsWhenNotFound {

        @Test
        @DisplayName("should not record any dispatch metrics when notification is missing")
        void shouldNotRecordMetricsWhenNotificationNotFound() {
            when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

            listener.onNotificationCreated(new NotificationCreatedEvent(99L));

            // No dispatch happened — no counters or timers should be registered
            assertThat(meterRegistry.find("notification.dispatched").counter()).isNull();
            assertThat(meterRegistry.find("notification.retry.total").counter()).isNull();
            assertThat(meterRegistry.find("notification.dispatch.duration").timer()).isNull();
        }
    }
}
