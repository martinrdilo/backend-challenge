package io.backend.notifications.repository;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Static factory methods for building type-safe {@link Specification} predicates
 * for {@link Notification} queries. Each method returns {@code null} when its
 * parameter is {@code null}, which Spring Data JPA ignores during composition.
 *
 * <p><strong>IDOR protection:</strong> Every query MUST start with
 * {@link #belongsToUser(String)} to enforce ownership isolation.</p>
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
        // utility class — no instances
    }

    /**
     * Filters notifications owned by the user with the given email.
     * Performs a join to the User table.
     *
     * @param email the authenticated user's email
     * @return a spec that restricts results to the given owner, or {@code null} if email is blank
     */
    public static Specification<Notification> belongsToUser(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("user").get("email"), email);
    }

    /**
     * Filters by notification status.
     *
     * @param status the status to match
     * @return an equality spec, or {@code null} if status is null
     */
    public static Specification<Notification> hasStatus(Status status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Filters by notification channel.
     *
     * @param channel the channel to match
     * @return an equality spec, or {@code null} if channel is null
     */
    public static Specification<Notification> hasChannel(Channel channel) {
        if (channel == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("channel"), channel);
    }

    /**
     * Filters notifications created on or after the given date (inclusive).
     * Converts the date to the start of day in {@link LocalDateTime}.
     *
     * @param date the inclusive start date
     * @return a greater-than-or-equal spec, or {@code null} if date is null
     */
    public static Specification<Notification> createdAfter(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDateTime startOfDay = date.atStartOfDay();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startOfDay);
    }

    /**
     * Filters notifications created on or before the given date (inclusive).
     * Converts the date to the end of day in {@link LocalDateTime}.
     *
     * @param date the inclusive end date
     * @return a less-than-or-equal spec, or {@code null} if date is null
     */
    public static Specification<Notification> createdBefore(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDateTime endOfDay = date.atTime(23, 59, 59, 999_999_999);
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endOfDay);
    }

    /**
     * Filters notifications whose title or content contains the given search term
     * (case-insensitive partial match via LIKE).
     *
     * @param search the search term
     * @return a spec matching title OR content, or {@code null} if search is null or blank
     */
    public static Specification<Notification> titleOrContentContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + search + "%";
        return (root, query, cb) -> {
            Predicate titleLike = cb.like(root.get("title"), pattern);
            Predicate contentLike = cb.like(root.get("content"), pattern);
            return cb.or(titleLike, contentLike);
        };
    }
}
