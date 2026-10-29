package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_correction_requests")
public class LocationCorrectionRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_profile_id", nullable = false)
    private VoterProfile voterProfile;
    @Embedded
    private RegisteredLocation location;
    @Column(name = "request_reason", nullable = false, length = 500)
    private String requestReason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private CorrectionStatus status = CorrectionStatus.PENDING;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "review_reason", length = 500)
    private String reviewReason;
    @Column(name = "source_reference", length = 80)
    private String sourceReference;
    @Column(name = "effective_at")
    private Instant effectiveAt;
    protected LocationCorrectionRequest() { }
    public LocationCorrectionRequest(VoterProfile profile, RegisteredLocation location, String reason, Instant now) {
        this.voterProfile = profile; this.location = location; this.requestReason = reason; this.requestedAt = now;
    }
    public UUID getId() { return id; }
    public VoterProfile getVoterProfile() { return voterProfile; }
    public RegisteredLocation getLocation() { return location == null ? RegisteredLocation.nationalOnly() : location; }
    public String getRequestReason() { return requestReason; }
    public CorrectionStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getReviewReason() { return reviewReason; }
    public String getSourceReference() { return sourceReference; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public void review(boolean approve, UUID actor, String reason, String reference, Instant effectiveAt, Instant now) {
        this.status = approve ? CorrectionStatus.APPROVED : CorrectionStatus.REJECTED;
        this.reviewedBy = actor; this.reviewReason = reason; this.sourceReference = reference;
        this.effectiveAt = effectiveAt; this.reviewedAt = now;
    }
    public void markApplied() { this.status = CorrectionStatus.APPLIED; }
}
