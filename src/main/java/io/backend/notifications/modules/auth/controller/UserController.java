package io.backend.notifications.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.backend.notifications.modules.auth.model.User;
import io.backend.notifications.modules.auth.repository.UserRepository;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping

    private ResponseEntity<User> findById(Long id) {
        return ResponseEntity.ok(userRepository.findById(id).orElseThrow());
    }
}
