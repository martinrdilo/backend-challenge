package io.backend.notifications.config;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds sample users and notifications on startup for development.
 * Runs only when the {@code test} profile is NOT active.
 *
 * <p><strong>Idempotent:</strong> Users are checked via {@code existsByEmail}
 * before insertion. Re-running the application won't create duplicates.</p>
 */
@Component
@Profile("!test")
public class SeedDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedDataInitializer(UserRepository userRepository,
                               NotificationRepository notificationRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("Seeding sample data...");

        User alice = createUserIfNotExists(
                "alice@example.com", "alice", "+541112345678", null);

        User bob = createUserIfNotExists(
                "bob@example.com", "bob", "+541198765432", null);

        if (notificationRepository.count() > 0) {
            log.info("Notifications already exist, skipping notification seed.");
            log.info("Seed data complete.");
            return;
        }

        createNotification(alice, "Welcome alert", "Welcome to the notification system",
                Channel.EMAIL, Status.SENT);

        createNotification(alice, "SMS promotion", "Check out our latest SMS offers!",
                Channel.SMS, Status.SENT);

        createNotification(alice, "Push reminder", "Don't forget your appointment tomorrow",
                Channel.PUSH, Status.PENDING);

        createNotification(bob, "Failed delivery", "Email could not be delivered to recipient",
                Channel.EMAIL, Status.FAILED);

        createNotification(alice, "System alert", "System maintenance scheduled for tonight",
                Channel.SMS, Status.SENT);

        log.info("Seed data complete.");
    }

    private User createUserIfNotExists(String email, String username, String phone, String deviceToken) {
        if (userRepository.existsByEmail(email)) {
            log.debug("User {} already exists, skipping.", email);
            return userRepository.findByEmail(email).orElseThrow();
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setPhone(phone);
        user.setDeviceToken(deviceToken);

        User saved = userRepository.save(user);
        log.info("Created user: {}", email);
        return saved;
    }

    private void createNotification(User user, String title, String content,
                                     Channel channel, Status status) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setChannel(channel);
        notification.setStatus(status);

        notificationRepository.save(notification);
        log.debug("Created notification: {}", title);
    }
}
