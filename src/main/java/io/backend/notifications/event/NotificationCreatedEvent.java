package io.backend.notifications.event;

/**
 * Published by {@code NotificationService} after a notification is saved, carrying the notification
 * ID so an async listener can load and dispatch it in its own transaction.
 */
public record NotificationCreatedEvent(Long notificationId) {}
