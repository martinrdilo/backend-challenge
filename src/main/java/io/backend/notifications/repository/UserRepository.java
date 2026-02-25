package io.backend.notifications.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import io.backend.notifications.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
