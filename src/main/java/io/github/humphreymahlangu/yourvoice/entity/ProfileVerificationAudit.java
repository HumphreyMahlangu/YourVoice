package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Private verification decisions only: never ballot choices, tokens, signatures or raw identity numbers. */
@Entity
@Table(name = "profile_verification_audit")
public class ProfileVerificationAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "voter_profile_id")
    private UUID voterProfileId;
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;
    @Column(name = "correction_request_id")
    private UUID correctionRequestId;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(name = "source_reference", length = 80)
    private String sourceReference;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "effective_at")
    private Instant effectiveAt;
    protected ProfileVerificationAudit() { }
    public ProfileVerificationAudit(UUID profile, UUID actor, UUID correction, String action, String source, String reason, Instant now, Instant effectiveAt) {
        this.voterProfileId = profile; this.actorUserId = actor; this.correctionRequestId = correction;
        this.action = action; this.sourceReference = source; this.reason = reason;
        this.occurredAt = now; this.effectiveAt = effectiveAt;
    }
    public UUID getId() { return id; }
    public UUID getVoterProfileId() { return voterProfileId; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getCorrectionRequestId() { return correctionRequestId; }
    public String getAction() { return action; }
    public String getSourceReference() { return sourceReference; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getEffectiveAt() { return effectiveAt; }
}
