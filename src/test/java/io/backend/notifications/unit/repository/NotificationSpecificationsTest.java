package io.backend.notifications.unit.repository;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.repository.NotificationSpecifications;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationSpecifications} static factory methods.
 * Each spec is tested in isolation with mocked JPA Criteria API objects.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class NotificationSpecificationsTest {

    @Mock
    private Root<Notification> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    // Typed paths to satisfy JPA generic bounds at compile time
    @Mock
    private Path<Status> statusPath;

    @Mock
    private Path<Channel> channelPath;

    @Mock
    private Path<LocalDateTime> createdAtPath;

    @Mock
    private Path<String> titlePath;

    @Mock
    private Path<String> contentPath;

    @Mock
    private Path<User> userPath;

    @Mock
    private Path<String> emailPath;

    @Mock
    private Predicate predicate;

    @Mock
    private Predicate anotherPredicate;

    @BeforeEach
    void setUp() {
        lenient().when(cb.conjunction()).thenReturn(mock(Predicate.class));
    }

    // ──── hasStatus ────

    @Test
    void hasStatusShouldCreateEqualPredicateWhenStatusProvided() {
        when(root.get("status")).thenReturn((Path) statusPath);
        when(cb.equal(statusPath, Status.SENT)).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.hasStatus(Status.SENT);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(cb).equal(statusPath, Status.SENT);
    }

    @Test
    void hasStatusShouldReturnNullWhenStatusIsNull() {
        Specification<Notification> spec = NotificationSpecifications.hasStatus(null);
        assertThat(spec).isNull();
    }

    // ──── hasChannel ────

    @Test
    void hasChannelShouldCreateEqualPredicateWhenChannelProvided() {
        when(root.get("channel")).thenReturn((Path) channelPath);
        when(cb.equal(channelPath, Channel.EMAIL)).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.hasChannel(Channel.EMAIL);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(cb).equal(channelPath, Channel.EMAIL);
    }

    @Test
    void hasChannelShouldReturnNullWhenChannelIsNull() {
        Specification<Notification> spec = NotificationSpecifications.hasChannel(null);
        assertThat(spec).isNull();
    }

    // ──── createdAfter ────

    @Test
    void createdAfterShouldCreateGreaterThanOrEqualPredicate() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        when(root.get("createdAt")).thenReturn((Path) createdAtPath);
        when(cb.greaterThanOrEqualTo(eq(createdAtPath), any(LocalDateTime.class))).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.createdAfter(date);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(cb).greaterThanOrEqualTo(eq(createdAtPath), any(LocalDateTime.class));
    }

    @Test
    void createdAfterShouldReturnNullWhenDateIsNull() {
        Specification<Notification> spec = NotificationSpecifications.createdAfter(null);
        assertThat(spec).isNull();
    }

    // ──── createdBefore ────

    @Test
    void createdBeforeShouldCreateLessThanOrEqualPredicate() {
        LocalDate date = LocalDate.of(2024, 12, 31);
        when(root.get("createdAt")).thenReturn((Path) createdAtPath);
        when(cb.lessThanOrEqualTo(eq(createdAtPath), any(LocalDateTime.class))).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.createdBefore(date);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(cb).lessThanOrEqualTo(eq(createdAtPath), any(LocalDateTime.class));
    }

    @Test
    void createdBeforeShouldReturnNullWhenDateIsNull() {
        Specification<Notification> spec = NotificationSpecifications.createdBefore(null);
        assertThat(spec).isNull();
    }

    // ──── titleOrContentContains ────

    @Test
    void titleOrContentContainsShouldCreateOrLikeExpression() {
        Path<String> lowerTitlePath = mock(Path.class);
        Path<String> lowerContentPath = mock(Path.class);

        when(root.get("title")).thenReturn((Path) titlePath);
        when(root.get("content")).thenReturn((Path) contentPath);
        when(cb.lower(titlePath)).thenReturn(lowerTitlePath);
        when(cb.lower(contentPath)).thenReturn(lowerContentPath);
        when(cb.like(eq(lowerTitlePath), eq("%alert%"))).thenReturn(predicate);
        when(cb.like(eq(lowerContentPath), eq("%alert%"))).thenReturn(anotherPredicate);
        when(cb.or(predicate, anotherPredicate)).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.titleOrContentContains("alert");
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void titleOrContentContainsShouldReturnNullWhenSearchIsNull() {
        Specification<Notification> spec = NotificationSpecifications.titleOrContentContains(null);
        assertThat(spec).isNull();
    }

    @Test
    void titleOrContentContainsShouldReturnNullWhenSearchIsBlank() {
        Specification<Notification> spec = NotificationSpecifications.titleOrContentContains("   ");
        assertThat(spec).isNull();
    }

    @Test
    void titleOrContentContainsShouldReturnNullWhenSearchIsEmpty() {
        Specification<Notification> spec = NotificationSpecifications.titleOrContentContains("");
        assertThat(spec).isNull();
    }

    // ──── belongsToUser ────

    @Test
    void belongsToUserShouldJoinUserAndFilterByEmail() {
        when(root.get("user")).thenReturn((Path) userPath);
        when(userPath.get("email")).thenReturn((Path) emailPath);
        when(cb.equal(emailPath, "alice@example.com")).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.belongsToUser("alice@example.com");
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        verify(root).get("user");
        verify(userPath).get("email");
    }

    @Test
    void belongsToUserShouldNotReturnNullWhenEmailIsValid() {
        Specification<Notification> spec = NotificationSpecifications.belongsToUser("alice@example.com");
        assertThat(spec).isNotNull();
    }

    // ──── AND composition ────

    @Test
    void andCompositionShouldCombineMultipleSpecs() {
        // Mock hasStatus
        when(root.get("status")).thenReturn((Path) statusPath);
        when(cb.equal(statusPath, Status.SENT)).thenReturn(predicate);

        // Mock hasChannel
        when(root.get("channel")).thenReturn((Path) channelPath);
        when(cb.equal(channelPath, Channel.EMAIL)).thenReturn(anotherPredicate);

        // Mock and
        Predicate combinedPredicate = mock(Predicate.class);
        when(cb.and(predicate, anotherPredicate)).thenReturn(combinedPredicate);

        Specification<Notification> statusSpec = NotificationSpecifications.hasStatus(Status.SENT);
        Specification<Notification> channelSpec = NotificationSpecifications.hasChannel(Channel.EMAIL);

        Specification<Notification> composed = statusSpec.and(channelSpec);
        Predicate result = composed.toPredicate(root, query, cb);

        assertThat(result).isSameAs(combinedPredicate);
        verify(cb).and(predicate, anotherPredicate);
    }

    // ──── ownership predicate includes user email join ────

    @Test
    void ownershipPredicateShouldIncludeUserEmailJoin() {
        String email = "owner@example.com";
        when(root.get("user")).thenReturn((Path) userPath);
        when(userPath.get("email")).thenReturn((Path) emailPath);
        when(cb.equal(emailPath, email)).thenReturn(predicate);

        Specification<Notification> spec = NotificationSpecifications.belongsToUser(email);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
        // Verify the chain: root -> user -> email -> equal
        verify(root).get("user");
        verify(userPath).get("email");
        verify(cb).equal(emailPath, email);
    }
}
