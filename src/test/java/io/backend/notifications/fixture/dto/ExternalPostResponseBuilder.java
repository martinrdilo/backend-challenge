package io.backend.notifications.fixture.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.backend.notifications.dto.ExternalPostResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluent builder for creating ExternalPostResponse DTOs and their JSON representation.
 *
 * Usage:
 *   // Single DTO
 *   ExternalPostResponse post = ExternalPostResponseBuilder.aPost().withUserId(1L).build();
 *
 *   // JSON array for WireMock stubs
 *   String json = ExternalPostResponseBuilder.aPost().withUserId(1L).buildListJson(3);
 */
public final class ExternalPostResponseBuilder {

    private static final AtomicLong COUNTER = new AtomicLong(1);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long userId;
    private Long id;
    private String title;
    private String body;

    private ExternalPostResponseBuilder() {
        long count = COUNTER.getAndIncrement();
        this.userId = 1L;
        this.id = count;
        this.title = "Post title " + count;
        this.body = "Post body content " + count;
    }

    public static ExternalPostResponseBuilder aPost() {
        return new ExternalPostResponseBuilder();
    }

    public ExternalPostResponseBuilder withUserId(Long userId) {
        this.userId = userId;
        return this;
    }

    public ExternalPostResponseBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public ExternalPostResponseBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public ExternalPostResponseBuilder withBody(String body) {
        this.body = body;
        return this;
    }

    /**
     * Builds a single ExternalPostResponse DTO.
     */
    public ExternalPostResponse build() {
        return new ExternalPostResponse(userId, id, title, body);
    }

    /**
     * Builds a list of ExternalPostResponse DTOs with incrementing ids.
     */
    public List<ExternalPostResponse> buildList(int count) {
        List<ExternalPostResponse> posts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            posts.add(new ExternalPostResponse(userId, id + i, title + " " + (i + 1), body + " " + (i + 1)));
        }
        return posts;
    }

    /**
     * Builds a JSON array string of posts — ready to use as WireMock response body.
     */
    public String buildListJson(int count) {
        try {
            return MAPPER.writeValueAsString(buildList(count));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize posts to JSON", e);
        }
    }

    /**
     * Builds a single post as JSON string.
     */
    public String buildJson() {
        try {
            return MAPPER.writeValueAsString(build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize post to JSON", e);
        }
    }
}
