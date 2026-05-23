package io.backend.notifications.service;

import io.backend.notifications.client.ExternalMediaClient;
import io.backend.notifications.dto.EnrichedNotificationResponse;
import io.backend.notifications.dto.ExternalPhotoResponse;
import io.backend.notifications.dto.NotificationCriteria;
import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.dto.NotificationUpdateRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.event.NotificationCreatedEvent;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.NotificationSpecifications;
import io.backend.notifications.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExternalMediaClient externalMediaClient;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ExternalMediaClient externalMediaClient,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.externalMediaClient = externalMediaClient;
        this.eventPublisher = eventPublisher;
    }

    public EnrichedNotificationResponse createNotification(NotificationRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(request.title());
        notification.setContent(request.content());
        notification.setChannel(request.channel());

        if (request.attachmentIds() != null) {
            notification.setAttachmentIds(request.attachmentIds());
        }

        Notification saved = notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId()));
        return enrichNotification(saved);
    }

    public EnrichedNotificationResponse getNotificationById(Long id) {
        Notification notification = findOwnNotification(id);
        return enrichNotification(notification);
    }

    public Notification findOwnNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!notification.getUser().getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return notification;
    }

    /**
     * Searches notifications with optional criteria and pagination.
     * Ownership is enforced via {@link NotificationSpecifications#belongsToUser(String)}.
     * Page size is capped at 100.
     *
     * @param criteria search filters (all null = no filter)
     * @param pageable pagination parameters
     * @return paginated enriched notifications belonging to the authenticated user
     */
    public Page<EnrichedNotificationResponse> searchNotifications(NotificationCriteria criteria, Pageable pageable) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // Cap page size at 100
        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }

        // Build specification chain — IDOR baked in from the start
        Specification<Notification> spec = NotificationSpecifications.belongsToUser(email);

        if (criteria.status() != null) {
            spec = spec.and(NotificationSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.channel() != null) {
            spec = spec.and(NotificationSpecifications.hasChannel(criteria.channel()));
        }
        if (criteria.createdAfter() != null) {
            spec = spec.and(NotificationSpecifications.createdAfter(criteria.createdAfter()));
        }
        if (criteria.createdBefore() != null) {
            spec = spec.and(NotificationSpecifications.createdBefore(criteria.createdBefore()));
        }
        if (criteria.search() != null) {
            spec = spec.and(NotificationSpecifications.titleOrContentContains(criteria.search()));
        }

        Page<Notification> page = notificationRepository.findAll(spec, pageable);
        return page.map(this::enrichNotification);
    }

    public EnrichedNotificationResponse updateNotification(Long id, NotificationUpdateRequest request) {
        Notification notification = findOwnNotification(id);
        notification.setTitle(request.title());
        notification.setContent(request.content());
        notification.setAttachmentIds(request.attachmentIds());
        Notification saved = notificationRepository.save(notification);
        return enrichNotification(saved);
    }

    public void deleteNotification(Long id) {
        Notification notification = findOwnNotification(id);
        notificationRepository.delete(notification);
    }

    private EnrichedNotificationResponse enrichNotification(Notification notification) {
        List<ExternalPhotoResponse> attachments = resolvePhotos(notification.getAttachmentIds());

        return new EnrichedNotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getCreatedAt(),
                notification.getUser().getId(),
                attachments
        );
    }

    private List<ExternalPhotoResponse> resolvePhotos(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .map(externalMediaClient::getPhotoById)
                .filter(Objects::nonNull)
                .toList();
    }
}
