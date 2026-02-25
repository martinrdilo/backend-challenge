package io.backend.notifications.repository;

import org.springframework.data.repository.CrudRepository;
import io.backend.notifications.entity.User;

public interface UserRepository extends CrudRepository<User, Long> {

}
