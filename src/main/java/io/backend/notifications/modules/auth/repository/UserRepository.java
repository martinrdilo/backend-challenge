package io.backend.notifications.modules.auth.repository;

import org.springframework.data.repository.CrudRepository;
import io.backend.notifications.modules.auth.model.User;

public interface UserRepository extends CrudRepository<User, Long> {

}
