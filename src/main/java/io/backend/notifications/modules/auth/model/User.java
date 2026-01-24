package io.backend.notifications.modules.auth.model;

import java.time.Instant;

record User(@Id Long id,
        String email,
        String password,
        Instant createdAt) {

}
