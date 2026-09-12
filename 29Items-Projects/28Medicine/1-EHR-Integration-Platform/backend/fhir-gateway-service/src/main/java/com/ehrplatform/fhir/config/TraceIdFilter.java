package com.ehrplatform.fhir.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a correlation id into the SLF4J {@link MDC} for every request, so logs,
 * the PHI audit trail, and error responses all share one {@code traceId}.
 * Honors an inbound {@code X-Trace-Id} / W3C {@code traceparent}, else generates one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolve(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private String resolve(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null && traceparent.split("-").length >= 2) {
            return traceparent.split("-")[1]; // W3C trace-id segment
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
