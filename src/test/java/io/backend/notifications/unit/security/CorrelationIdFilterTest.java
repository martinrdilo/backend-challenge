package io.backend.notifications.unit.security;

import io.backend.notifications.security.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CorrelationIdFilter} — TDD RED phase.
 *
 * <p>Spec scenarios:
 * <ul>
 *   <li>#1: Header passthrough — X-Correlation-Id present → MDC correlationId = header value</li>
 *   <li>#2: UUID generation — no header → MDC correlationId = new UUID</li>
 *   <li>#3: Request attribute — correlation ID also set as request attribute</li>
 *   <li>#4: MDC cleanup — after filter chain, MDC correlationId cleared</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Mock
    private FilterChain chain;

    @Captor
    private ArgumentCaptor<MockHttpServletRequest> requestCaptor;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Nested
    @DisplayName("header passthrough")
    class HeaderPassthrough {

        @Test
        @DisplayName("should use X-Correlation-Id when present")
        void shouldUseCorrelationIdHeaderWhenPresent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Correlation-Id", "abc-123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(request.getAttribute("correlationId")).isEqualTo("abc-123");
        }

        @Test
        @DisplayName("should propagate non-UUID correlation IDs faithfully")
        void shouldPropagateAnyCorrelationIdValue() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Correlation-Id", "trace-999-xyz");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(request.getAttribute("correlationId")).isEqualTo("trace-999-xyz");
        }
    }

    @Nested
    @DisplayName("UUID generation")
    class UuidGeneration {

        @Test
        @DisplayName("should generate UUID when X-Correlation-Id is absent")
        void shouldGenerateUuidWhenHeaderAbsent() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            String correlationId = (String) request.getAttribute("correlationId");
            assertThat(correlationId).isNotNull();
            assertThat(correlationId).isNotEmpty();
            // UUID format: 8-4-4-4-12 hex digits
            assertThat(correlationId).matches(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        }

        @Test
        @DisplayName("should generate different UUIDs for different requests")
        void shouldGenerateUniqueUuidPerRequest() throws Exception {
            MockHttpServletRequest request1 = new MockHttpServletRequest();
            MockHttpServletResponse response1 = new MockHttpServletResponse();
            MockHttpServletRequest request2 = new MockHttpServletRequest();
            MockHttpServletResponse response2 = new MockHttpServletResponse();

            filter.doFilter(request1, response1, chain);
            filter.doFilter(request2, response2, chain);

            String id1 = (String) request1.getAttribute("correlationId");
            String id2 = (String) request2.getAttribute("correlationId");
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("MDC lifecycle")
    class MdcLifecycle {

        @Test
        @DisplayName("should clear MDC correlationId after filter completes")
        void shouldClearMdcAfterFilterCompletes() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Correlation-Id", "abc-123");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(MDC.get("correlationId")).isNull();
        }

        @Test
        @DisplayName("should clear MDC even when downstream filter throws")
        void shouldClearMdcEvenWhenDownstreamThrows() throws Exception {
            doThrow(new RuntimeException("downstream failure"))
                    .when(chain).doFilter(any(), any());

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Correlation-Id", "error-case");
            MockHttpServletResponse response = new MockHttpServletResponse();

            try {
                filter.doFilter(request, response, chain);
            } catch (RuntimeException ignored) {
                // expected
            }

            assertThat(MDC.get("correlationId")).isNull();
        }
    }

    @Nested
    @DisplayName("filter chain invocation")
    class FilterChainInvocation {

        @Test
        @DisplayName("should invoke the rest of the filter chain")
        void shouldInvokeFilterChain() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(eq(request), eq(response));
        }

        @Test
        @DisplayName("should set correlationId as request attribute before chain")
        void shouldSetAttributeBeforeChainProceeds() throws Exception {
            doAnswer(invocation -> {
                MockHttpServletRequest req = invocation.getArgument(0);
                assertThat(req.getAttribute("correlationId")).isNotNull();
                return null;
            }).when(chain).doFilter(any(MockHttpServletRequest.class), any());

            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(eq(request), eq(response));
        }
    }
}
