package io.backend.notifications.controller;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.backend.notifications.entity.User;
import io.backend.notifications.repository.UserRepository;

@RestController
@RequestMapping("/users")
public class UserController {

    private final Use userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    private ResponseEntity<User> findById(Long id) {
        Optional<User> userOptional = userRepository.findById(id);

        if (userOptional.isPresent()) {
            return ResponseEntity.ok(userOptional.get());
        }

        return ResponseEntity.notFound().build();
    }

}
