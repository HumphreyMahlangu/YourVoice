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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voter_verifications")
public class VoterVerification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_profile_id", nullable = false, unique = true)
    private VoterProfile voterProfile;
    @Enumerated(EnumType.STRING) @Column(name = "identity_status", nullable = false, length = 32)
    private VerificationStatus identityStatus = VerificationStatus.UNVERIFIED;
    @Enumerated(EnumType.STRING) @Column(name = "location_status", nullable = false, length = 32)
    private VerificationStatus locationStatus = VerificationStatus.UNVERIFIED;
    @Column(nullable = false)
    private boolean established;
    @Column(name = "source_reference", length = 80)
    private String sourceReference;
    @Embedded
    private RegisteredLocation location;
    @Column(nullable = false, length = 500)
    private String message = "Request a check against the fictional simulation registry.";
    @Column(name = "checked_at")
    private Instant checkedAt;
    @Column(name = "effective_at")
    private Instant effectiveAt;
    protected VoterVerification() { }
    public VoterVerification(VoterProfile voterProfile) { this.voterProfile = voterProfile; }
    public UUID getId() { return id; }
    public VoterProfile getVoterProfile() { return voterProfile; }
    public VerificationStatus getIdentityStatus() { return identityStatus; }
    public VerificationStatus getLocationStatus() { return locationStatus; }
    public boolean isEstablished() { return established; }
    public String getSourceReference() { return sourceReference; }
    public RegisteredLocation getLocation() { return location == null ? RegisteredLocation.nationalOnly() : location; }
    public String getMessage() { return message; }
    public Instant getCheckedAt() { return checkedAt; }
    public Instant getEffectiveAt() { return effectiveAt; }
    /** Location review never re-grants identity after a subsequent rejected or pending check. */
    public void applyApprovedLocation(RegisteredLocation location, String reference, Instant effectiveAt) {
        this.location = location;
        this.locationStatus = location.getProvince() == null ? VerificationStatus.UNVERIFIED : VerificationStatus.VERIFIED;
        if (identityStatus == VerificationStatus.VERIFIED) this.sourceReference = reference;
        this.effectiveAt = effectiveAt;
        this.message = "Approved fictional area correction is effective. Existing registrations keep their saved area. Review your identity status before registering or voting.";
    }
    public void recordOutcome(VerificationStatus identity, VerificationStatus area, String reference,
            RegisteredLocation location, String message, Instant now) {
        this.identityStatus = identity; this.locationStatus = area; this.sourceReference = reference;
        if (identity == VerificationStatus.VERIFIED) {
            this.established = true;
            this.location = area == VerificationStatus.VERIFIED ? location : RegisteredLocation.nationalOnly();
            this.effectiveAt = now;
        }
        this.message = message; this.checkedAt = now;
    }
}
