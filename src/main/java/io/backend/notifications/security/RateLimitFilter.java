package io.backend.notifications.security;

import io.backend.notifications.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REGISTER_PATH = "/auth/register";

    private final RateLimitConfig config;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!LOGIN_PATH.equals(path) && !REGISTER_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        Bandwidth limit;
        ConcurrentHashMap<String, Bucket> bucketStore;
        String resourceName;

        if (LOGIN_PATH.equals(path)) {
            limit = Bandwidth.simple(config.loginAttemptsPerMinute(),
                    Duration.ofMinutes(config.windowMinutes()));
            bucketStore = loginBuckets;
            resourceName = "login";
        } else {
            limit = Bandwidth.simple(config.registerAttemptsPerMinute(),
                    Duration.ofMinutes(config.windowMinutes()));
            bucketStore = registerBuckets;
            resourceName = "register";
        }

        Bucket bucket = bucketStore.computeIfAbsent(clientIp, ip -> Bucket.builder().addLimit(limit).build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP {} on {}", clientIp, resourceName);

            long retryAfterSeconds = bucket.estimateAbilityToConsume(1)
                    .getNanosToWaitForRefill() / 1_000_000_000L + 1;

            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    String.format("Too many %s attempts. Retry after %ds.", resourceName, retryAfterSeconds));
            problem.setProperty("retryAfterSeconds", retryAfterSeconds);
            problem.setInstance(java.net.URI.create(path));

            response.setStatus(429);
            response.setContentType("application/problem+json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs (comma-separated); take the first one
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
