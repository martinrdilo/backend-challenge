package io.backend.notifications.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.dto.UserResponse;
import io.backend.notifications.entity.User;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.repository.UserRepository;
import io.backend.notifications.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Unit test for UserService. No Spring context — pure Mockito. Fast execution. */
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserService userService;

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
  void shouldUpdateUser() {
    User existing = UserBuilder.aUser().build();
    existing.setId(1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(userRepository.save(any(User.class))).thenReturn(existing);

    UserRequest request = new UserRequest("newusername", "new@test.com");
    UserResponse result = userService.update(1L, request);

    assertThat(result.username()).isEqualTo("newusername");
    assertThat(result.email()).isEqualTo("new@test.com");
    verify(userRepository).findById(1L);
    verify(userRepository).save(existing);
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
