package io.backend.notifications.dto;

import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import java.time.LocalDate;

/**
 * Immutable search criteria for filtering notifications. All fields are optional — {@code null}
 * means "no filter".
 *
 * @param status match notifications with this status, or {@code null} for any
 * @param channel match notifications with this channel, or {@code null} for any
 * @param createdAfter match notifications created on or after this date, or {@code null} for any
 * @param createdBefore match notifications created on or before this date, or {@code null} for any
 * @param search match notifications whose title or content contains this text, or {@code null} for
 *     any
 */
public record NotificationCriteria(
    Status status,
    Channel channel,
    LocalDate createdAfter,
    LocalDate createdBefore,
    String search) {}
