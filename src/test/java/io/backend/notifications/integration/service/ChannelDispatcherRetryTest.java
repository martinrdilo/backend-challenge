package io.backend.notifications.integration.service;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.UserRepository;
import io.backend.notifications.service.channel.ChannelDispatcher;
import io.backend.notifications.service.channel.EmailChannelSender;
import io.backend.notifications.service.channel.PushChannelSender;
import io.backend.notifications.service.channel.SmsChannelSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying retry behavior on {@link ChannelDispatcher#dispatch}
 * when annotated with {@code @Retryable}.
 *
 * <p>Tests call {@code channelDispatcher.dispatch(notification)} directly
 * to isolate retry behavior from the async event dispatch layer.
 */
@DisplayName("ChannelDispatcher Retry")
@Import(ChannelDispatcherRetryTest.TestConfig.class)
class ChannelDispatcherRetryTest extends AbstractIntegrationTest {

    @Autowired
    private ChannelDispatcher channelDispatcher;

    @Autowired
    private UserRepository userRepository;

    private Notification notification;

    @BeforeEach
    void setUp() {
        // Reset mock state from previous test
        reset(TestConfig.emailMock, TestConfig.smsMock, TestConfig.pushMock);

        // Reconfigure channels after reset
        when(TestConfig.emailMock.getChannel()).thenReturn(Channel.EMAIL);
        when(TestConfig.smsMock.getChannel()).thenReturn(Channel.SMS);
        when(TestConfig.pushMock.getChannel()).thenReturn(Channel.PUSH);

        // Create a test user and notification
        User user = new User();
        user.setUsername("retrytest");
        user.setEmail("retry@test.com");
        user.setPasswordHash("ignored_hash");
        user.setPhone("+541112345678");
        userRepository.save(user);

        notification = new Notification();
        notification.setUser(user);
        notification.setTitle("Retry Test");
        notification.setContent("Content");
        notification.setChannel(Channel.EMAIL);
        notification.setStatus(Status.PENDING);
    }

    // ──── Retry then success ────

    @Test
    @DisplayName("should retry twice and succeed on third attempt")
    void shouldRetryTwiceAndSucceedOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        doAnswer(invocation -> {
            int call = attempts.incrementAndGet();
            if (call < 3) {
                throw new RuntimeException("Transient error — attempt " + call);
            }
            return null;
        }).when(TestConfig.emailMock).send(any());

        channelDispatcher.dispatch(notification);

        verify(TestConfig.emailMock, times(3)).send(any());
    }

    // ──── All retries exhausted ────

    @Test
    @DisplayName("should retry three times and throw when all attempts fail")
    void shouldRetryThreeTimesAndFailWhenExhausted() {
        doThrow(new RuntimeException("Always failing"))
                .when(TestConfig.emailMock).send(any());

        assertThatThrownBy(() -> channelDispatcher.dispatch(notification))
                .isInstanceOf(RuntimeException.class);

        verify(TestConfig.emailMock, times(3)).send(any());
    }

    // ──── No retry on IllegalStateException ────

    @Test
    @DisplayName("should NOT retry when sender throws IllegalStateException")
    void shouldNotRetryOnIllegalStateException() {
        doThrow(new IllegalStateException("Validation error"))
                .when(TestConfig.emailMock).send(any());

        assertThatThrownBy(() -> channelDispatcher.dispatch(notification))
                .isInstanceOf(IllegalStateException.class);

        verify(TestConfig.emailMock, times(1)).send(any());
    }

    // ──── Test Configuration ────

    @TestConfiguration
    static class TestConfig {

        static EmailChannelSender emailMock;
        static SmsChannelSender smsMock;
        static PushChannelSender pushMock;

        @Bean
        @Primary
        ChannelDispatcher testChannelDispatcher() {
            emailMock = mock(EmailChannelSender.class);
            smsMock = mock(SmsChannelSender.class);
            pushMock = mock(PushChannelSender.class);

            when(emailMock.getChannel()).thenReturn(Channel.EMAIL);
            when(smsMock.getChannel()).thenReturn(Channel.SMS);
            when(pushMock.getChannel()).thenReturn(Channel.PUSH);

            return new ChannelDispatcher(List.of(emailMock, smsMock, pushMock));
        }
    }
}
