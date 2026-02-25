package io.backend.notifications.service;

import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.dto.UserResponse;
import io.backend.notifications.entity.User;
import io.backend.notifications.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> findAll() {

        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse findById(Long id) {

        User user = findEntityById(id);
        return toResponse(user);
    }

    public UserResponse create(UserRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        return toResponse(userRepository.save(user));
    }

    public UserResponse update(Long id, UserRequest request) {
        User existing = findEntityById(id);
        existing.setUsername(request.username());
        existing.setEmail(request.email());
        return toResponse(existing);
    }

    public void delete(Long id) {
        findEntityById(id);
        userRepository.deleteById(id);
    }


    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }

    private User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

}
