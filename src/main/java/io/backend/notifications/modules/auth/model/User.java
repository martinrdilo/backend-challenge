package io.backend.notifications.modules.auth.model;

import java.time.Instant;

public record User(Long id,
        String email,
        String password,
        Instant createdAt) {

}
