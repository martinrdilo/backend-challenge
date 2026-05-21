package io.backend.notifications.controller;

import io.backend.notifications.dto.EnrichedNotificationResponse;
import io.backend.notifications.dto.NotificationCriteria;
import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.dto.NotificationUpdateRequest;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<EnrichedNotificationResponse> createNotification(
            @Valid @RequestBody NotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createNotification(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnrichedNotificationResponse> getNotificationById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getNotificationById(id));
    }

    @GetMapping
    public ResponseEntity<Page<EnrichedNotificationResponse>> getMyNotifications(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Channel channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdBefore,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        NotificationCriteria criteria = new NotificationCriteria(status, channel, createdAfter, createdBefore, search);
        return ResponseEntity.ok(notificationService.searchNotifications(criteria, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnrichedNotificationResponse> updateNotification(
            @PathVariable Long id,
            @Valid @RequestBody NotificationUpdateRequest request) {
        return ResponseEntity.ok(notificationService.updateNotification(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}
