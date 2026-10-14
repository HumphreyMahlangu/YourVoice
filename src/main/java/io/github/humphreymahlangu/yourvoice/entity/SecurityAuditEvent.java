package io.github.humphreymahlangu.votetrust.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_audit_events")
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private SecurityAuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SecurityAuditOutcome outcome;

    @Column(name = "principal_user_id")
    private UUID principalUserId;

    @Column(name = "principal_email", length = 320)
    private String principalEmail;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(length = 512)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SecurityAuditEvent() {
    }

    public SecurityAuditEvent(
            SecurityAuditEventType eventType,
            SecurityAuditOutcome outcome,
            UUID principalUserId,
            String principalEmail,
            String clientIp,
            String userAgent,
            String detail,
            Instant occurredAt
    ) {
        this.eventType = eventType;
        this.outcome = outcome;
        this.principalUserId = principalUserId;
        this.principalEmail = principalEmail;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public SecurityAuditEventType getEventType() {
        return eventType;
    }

    public SecurityAuditOutcome getOutcome() {
        return outcome;
    }

    public UUID getPrincipalUserId() {
        return principalUserId;
    }

    public String getPrincipalEmail() {
        return principalEmail;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
