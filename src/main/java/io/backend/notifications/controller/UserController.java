package io.backend.notifications.controller;

import java.util.List;
import io.backend.notifications.dto.MergedNotificationsResponse;
import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.dto.UserResponse;
import io.backend.notifications.service.NotificationService;
import io.backend.notifications.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;

    public UserController(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest userRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(userRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable long id,
                                               @Valid @RequestBody UserRequest userRequest) {
        return ResponseEntity.ok(userService.update(id, userRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponse> delete(@PathVariable long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/external-notifications")
    public ResponseEntity<MergedNotificationsResponse> getExternalNotifications(@PathVariable long id) {
        return ResponseEntity.ok(notificationService.findMergedByUserId(id));
    }
}
