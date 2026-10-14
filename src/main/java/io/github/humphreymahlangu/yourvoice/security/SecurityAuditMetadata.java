package io.github.humphreymahlangu.votetrust.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

public record SecurityAuditMetadata(
        String clientIp,
        String userAgent
) {

    public static SecurityAuditMetadata from(HttpServletRequest request) {
        return new SecurityAuditMetadata(
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT)
        );
    }

    public static SecurityAuditMetadata system() {
        return new SecurityAuditMetadata(null, null);
    }
}
