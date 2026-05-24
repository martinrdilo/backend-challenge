package io.backend.notifications.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.entity.User;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Integration test for UserRepository. Proves that Testcontainers PostgreSQL works correctly. */
class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    cleanDatabase();
  }

  @Test
  void shouldSaveAndFindUser() {
    User user = UserBuilder.aUser().build();

    User saved = userRepository.save(user);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getUsername()).isEqualTo(user.getUsername());
    assertThat(saved.getEmail()).isEqualTo(user.getEmail());
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void shouldFindUserById() {
    User user = UserBuilder.aUser().build();
    User saved = userRepository.save(user);

    Optional<User> found = userRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getUsername()).isEqualTo(user.getUsername());
  }

  @Test
  void shouldReturnEmptyWhenUserNotFound() {
    Optional<User> found = userRepository.findById(999L);

    assertThat(found).isEmpty();
  }

  @Test
  void shouldDeleteUser() {
    User user = UserBuilder.aUser().build();
    User saved = userRepository.save(user);

    userRepository.deleteById(saved.getId());

    assertThat(userRepository.findById(saved.getId())).isEmpty();
  }
}
