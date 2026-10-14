package io.github.humphreymahlangu.votetrust.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditOutcome;
import io.github.humphreymahlangu.votetrust.observability.VoteTrustMetrics;
import io.github.humphreymahlangu.votetrust.service.SecurityAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final SecurityAuditService securityAuditService;
    private final VoteTrustMetrics metrics;

    public RateLimitingFilter(
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper,
            SecurityAuditService securityAuditService,
            VoteTrustMetrics metrics
    ) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
        this.securityAuditService = securityAuditService;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<RateLimitedEndpoint> endpoint = resolveEndpoint(request);
        if (endpoint.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitedEndpoint rateLimitedEndpoint = endpoint.get();
        RateLimitDecision decision = rateLimitService.consume(
                rateLimitedEndpoint.bucket(),
                request.getRemoteAddr(),
                rateLimitedEndpoint.limit()
        );

        if (decision.allowed()) {
            response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remainingRequests()));
            filterChain.doFilter(request, response);
            return;
        }

        auditBlockedRequest(request, rateLimitedEndpoint);
        metrics.rateLimitBlocked(rateLimitedEndpoint.bucket());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(decision.retryAfterSeconds()));
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        "Too many requests. Retry after " + decision.retryAfterSeconds() + " seconds.",
                        request.getRequestURI()
                )
        );
    }

    private Optional<RateLimitedEndpoint> resolveEndpoint(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return Optional.empty();
        }

        String path = request.getServletPath();
        if ("/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/refresh".equals(path)
                || "/api/v1/auth/logout".equals(path)) {
            return Optional.of(new RateLimitedEndpoint("auth", rateLimitProperties.getAuthLimit()));
        }

        if ("/api/v1/admin/bootstrap".equals(path)) {
            return Optional.of(new RateLimitedEndpoint("admin-bootstrap", rateLimitProperties.getBootstrapLimit()));
        }

        if (path.matches("^/api/v1/elections/[^/]+/contests/[^/]+/credentials$")) {
            return Optional.of(new RateLimitedEndpoint("credential-issuance", rateLimitProperties.getCredentialLimit()));
        }

        if ("/api/v1/ballots".equals(path)) {
            return Optional.of(new RateLimitedEndpoint("ballot-submission", rateLimitProperties.getBallotLimit()));
        }

        return Optional.empty();
    }

    private void auditBlockedRequest(HttpServletRequest request, RateLimitedEndpoint endpoint) {
        try {
            securityAuditService.record(
                    SecurityAuditEventType.RATE_LIMIT_BLOCKED,
                    SecurityAuditOutcome.BLOCKED,
                    null,
                    null,
                    SecurityAuditMetadata.from(request),
                    endpoint.bucket() + " limit exceeded for " + request.getMethod() + " " + request.getRequestURI()
            );
        } catch (RuntimeException ignored) {
            // The request is already blocked. A transient audit write failure must not turn 429 into 500.
        }
    }

    private record RateLimitedEndpoint(String bucket, int limit) {
    }
}
