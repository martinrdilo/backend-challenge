package io.backend.notifications.fixture.entity;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluent builder for creating Notification entities in tests.
 *
 * Usage:
 *   Notification n = NotificationBuilder.aNotification().withUser(user).build();
 *   Notification custom = NotificationBuilder.aNotification()
 *       .withUser(user)
 *       .withTitle("Alert")
 *       .withChannel(Channel.SMS)
 *       .build();
 */
public final class NotificationBuilder {

    private static final AtomicLong COUNTER = new AtomicLong(1);

    private User user;
    private String title;
    private String content;
    private Channel channel;
    private Status status;

    private NotificationBuilder() {
        long id = COUNTER.getAndIncrement();
        this.title = "Test Notification " + id;
        this.content = "Content for notification " + id;
        this.channel = Channel.EMAIL;
        this.status = Status.PENDING;
    }

    public static NotificationBuilder aNotification() {
        return new NotificationBuilder();
    }

    public NotificationBuilder withUser(User user) {
        this.user = user;
        return this;
    }

    public NotificationBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public NotificationBuilder withContent(String content) {
        this.content = content;
        return this;
    }

    public NotificationBuilder withChannel(Channel channel) {
        this.channel = channel;
        return this;
    }

    public NotificationBuilder withStatus(Status status) {
        this.status = status;
        return this;
    }

    /**
     * Builds a Notification entity (without id — JPA will generate it on persist).
     * Requires a User to be set via withUser().
     */
    public Notification build() {
        if (user == null) {
            throw new IllegalStateException("Notification requires a User. Call withUser() before build().");
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setChannel(channel);
        notification.setStatus(status);
        return notification;
    }
}
