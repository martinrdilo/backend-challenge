package io.backend.notifications.repository;

import org.springframework.data.repository.CrudRepository;
import io.backend.notifications.entity.Notification;

public interface NotificationRepository extends CrudRepository<Notification, Long> {

}
