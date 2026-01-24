package io.backend.notifications.modules.notifications.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.backend.notifications.modules.notifications.model.Notification;
import io.backend.notifications.modules.notifications.repository.NotificationRepository;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/{id}")
    private ResponseEntity<Notification> findById(Long id) {
        Optional<Notification> notificationOptional = notificationRepository.findById(id);

        if (notificationOptional.isPresent()) {
            return ResponseEntity.ok(notificationOptional.get());
        }

        return ResponseEntity.notFound().build();
    }

}
