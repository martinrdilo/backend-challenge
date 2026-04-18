package io.backend.notifications.fixture.entity;

import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.entity.User;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluent builder for creating User entities and UserRequest DTOs in tests.
 *
 * Usage:
 *   User user = UserBuilder.aUser().build();
 *   User custom = UserBuilder.aUser().withUsername("john").withEmail("john@test.com").build();
 *   UserRequest request = UserBuilder.aUser().buildRequest();
 */
public final class UserBuilder {

    private static final AtomicLong COUNTER = new AtomicLong(1);

    private String username;
    private String email;

    private UserBuilder() {
        long id = COUNTER.getAndIncrement();
        this.username = "user-" + id;
        this.email = "user-" + id + "@test.com";
    }

    public static UserBuilder aUser() {
        return new UserBuilder();
    }

    public UserBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public UserBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    /**
     * Builds a User entity (without id — JPA will generate it on persist).
     */
    public User build() {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    /**
     * Builds a UserRequest DTO (for controller tests).
     */
    public UserRequest buildRequest() {
        return new UserRequest(username, email);
    }
}
