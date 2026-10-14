package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Admin-visible security audit event")
public record SecurityAuditEventResponse(
        @Schema(description = "Audit event identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Security event type", example = "USER_LOGIN")
        String eventType,

        @Schema(description = "Event outcome", example = "FAILURE")
        String outcome,

        @Schema(description = "Principal account identifier when applicable", example = "22222222-2222-2222-2222-222222222222")
        UUID principalUserId,

        @Schema(description = "Principal email when applicable", example = "voter@example.com")
        String principalEmail,

        @Schema(description = "Client IP address observed by the API", example = "127.0.0.1")
        String clientIp,

        @Schema(description = "User-Agent header supplied by the client")
        String userAgent,

        @Schema(description = "Short event detail without secrets or voting identifiers", example = "Invalid password")
        String detail,

        @Schema(description = "UTC timestamp when the event occurred", example = "2026-08-07T10:15:30Z")
        Instant occurredAt
) {

    public static SecurityAuditEventResponse from(SecurityAuditEvent event) {
        return new SecurityAuditEventResponse(
                event.getId(),
                event.getEventType().name(),
                event.getOutcome().name(),
                event.getPrincipalUserId(),
                event.getPrincipalEmail(),
                event.getClientIp(),
                event.getUserAgent(),
                event.getDetail(),
                event.getOccurredAt()
        );
    }
}
