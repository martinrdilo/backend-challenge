package io.backend.notifications.unit.service;

import io.backend.notifications.client.ExternalPostClient;
import io.backend.notifications.dto.ExternalPostResponse;
import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.dto.UserResponse;
import io.backend.notifications.entity.User;
import io.backend.notifications.fixture.dto.ExternalPostResponseBuilder;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.repository.UserRepository;
import io.backend.notifications.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for UserService.
 * No Spring context — pure Mockito. Fast execution.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExternalPostClient externalPostClient;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldReturnAllUsers() {
        User user = UserBuilder.aUser().build();
        user.setId(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<UserResponse> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().username()).isEqualTo(user.getUsername());
        verify(userRepository).findAll();
    }

    @Test
    void shouldFindUserById() {
        User user = UserBuilder.aUser().build();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.findById(1L);

        assertThat(result.username()).isEqualTo(user.getUsername());
        verify(userRepository).findById(1L);
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void shouldCreateUser() {
        UserRequest request = UserBuilder.aUser().buildRequest();
        User saved = UserBuilder.aUser().build();
        saved.setId(1L);
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse result = userService.create(request);

        assertThat(result.id()).isEqualTo(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldGetExternalNotifications() {
        User user = UserBuilder.aUser().build();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        List<ExternalPostResponse> posts = ExternalPostResponseBuilder.aPost()
                .withUserId(1L)
                .buildList(3);
        when(externalPostClient.getPostsByUser(1L)).thenReturn(posts);

        List<ExternalPostResponse> result = userService.getExternalNotifications(1L);

        assertThat(result).hasSize(3);
        verify(userRepository).findById(1L);
        verify(externalPostClient).getPostsByUser(1L);
    }

    @Test
    void shouldThrowWhenGettingNotificationsForNonExistentUser() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getExternalNotifications(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");

        verify(externalPostClient, never()).getPostsByUser(any());
    }

    @Test
    void shouldDeleteUser() {
        User user = UserBuilder.aUser().build();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.delete(1L);

        verify(userRepository).deleteById(1L);
    }
}
