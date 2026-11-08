package com.myapp.account_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    @Test
    void propagatesRequestIdAndClearsThreadContextOnFailure() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "test-request-42");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(
                        () ->
                                new RequestIdFilter()
                                        .doFilter(
                                                request,
                                                response,
                                                (req, res) -> {
                                                    assertThat(MDC.get("requestId"))
                                                            .isEqualTo("test-request-42");
                                                    throw new ServletException("failed");
                                                }))
                .isInstanceOf(ServletException.class);
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("test-request-42");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUntrustedHeader() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "invalid\r\nforged-log");
        var response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (req, res) -> {});
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");
        assertThat(MDC.get("requestId")).isNull();
    }
}
