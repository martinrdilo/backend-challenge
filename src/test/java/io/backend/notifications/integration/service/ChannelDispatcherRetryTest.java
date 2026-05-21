package io.backend.notifications.integration.service;

import io.backend.notifications.dto.EnrichedNotificationResponse;
import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.UserRepository;
import io.backend.notifications.service.NotificationService;
import io.backend.notifications.service.channel.ChannelDispatcher;
import io.backend.notifications.service.channel.ChannelSender;
import io.backend.notifications.service.channel.EmailChannelSender;
import io.backend.notifications.service.channel.PushChannelSender;
import io.backend.notifications.service.channel.SmsChannelSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying retry behavior on {@link ChannelDispatcher#dispatch}
 * when annotated with {@code @Retryable}.
 *
 * <p>A {@code @TestConfiguration} replaces the {@code ChannelDispatcher} bean with
 * one backed by pre-configured mock senders, so retry counts and exception behavior
 * are controllable without interfering with production sender beans.
 */
@DisplayName("ChannelDispatcher Retry")
@Import(ChannelDispatcherRetryTest.TestConfig.class)
class ChannelDispatcherRetryTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUpMocksAndAuth() {
        // Reset mock state from previous test
        reset(TestConfig.emailMock, TestConfig.smsMock, TestConfig.pushMock);

        // Reconfigure channels after reset (reset clears all stubbing)
        when(TestConfig.emailMock.getChannel()).thenReturn(Channel.EMAIL);
        when(TestConfig.smsMock.getChannel()).thenReturn(Channel.SMS);
        when(TestConfig.pushMock.getChannel()).thenReturn(Channel.PUSH);

        // Create a test user in the database
        User user = new User();
        user.setUsername("retrytest");
        user.setEmail("retry@test.com");
        user.setPasswordHash("ignored_hash");
        user.setPhone("+541112345678");
        userRepository.save(user);

        // Authenticate as that user
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("retry@test.com", null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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
            return null; // void method: third call succeeds
        }).when(TestConfig.emailMock).send(any());

        NotificationRequest request = new NotificationRequest(
                "Retry Test", "Content", Channel.EMAIL, List.of());

        EnrichedNotificationResponse response = notificationService.createNotification(request);

        assertThat(response.status()).isEqualTo("SENT");
        verify(TestConfig.emailMock, times(3)).send(any());
    }

    // ──── All retries exhausted ────

    @Test
    @DisplayName("should retry three times and mark FAILED when all attempts fail")
    void shouldRetryThreeTimesAndFailWhenExhausted() {
        doThrow(new RuntimeException("Always failing"))
                .when(TestConfig.emailMock).send(any());

        NotificationRequest request = new NotificationRequest(
                "Always Fail", "Content", Channel.EMAIL, List.of());

        EnrichedNotificationResponse response = notificationService.createNotification(request);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(TestConfig.emailMock, times(3)).send(any());
    }

    // ──── No retry on IllegalStateException ────

    @Test
    @DisplayName("should NOT retry when sender throws IllegalStateException")
    void shouldNotRetryOnIllegalStateException() {
        doThrow(new IllegalStateException("Validation error"))
                .when(TestConfig.emailMock).send(any());

        NotificationRequest request = new NotificationRequest(
                "Invalid", "Content", Channel.EMAIL, List.of());

        EnrichedNotificationResponse response = notificationService.createNotification(request);

        assertThat(response.status()).isEqualTo("FAILED");
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
