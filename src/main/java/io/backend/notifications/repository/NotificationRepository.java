package io.backend.notifications.repository;

import io.backend.notifications.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {

    List<Notification> findAllByUserId(Long userId);

    List<Notification> findAllByUserEmail(String email);
}
