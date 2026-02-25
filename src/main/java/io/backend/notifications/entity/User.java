package io.backend.notifications.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;

public record User(
        @Id Long id,
        String email,
        String password,
        Instant createdAt) {

}
