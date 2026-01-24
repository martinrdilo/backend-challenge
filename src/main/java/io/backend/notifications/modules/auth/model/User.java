package io.backend.notifications.modules.auth.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;

public record User(@Id Long id,
                String email,
                String password,
                Instant createdAt) {

}
