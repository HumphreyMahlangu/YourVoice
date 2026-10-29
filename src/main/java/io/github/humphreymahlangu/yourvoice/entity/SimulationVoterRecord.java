package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** An explicitly fictional, administrator-configured source. Contains no raw identity number. */
@Entity
@Table(name = "simulation_voter_records")
public class SimulationVoterRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Generated(event = EventType.INSERT)
    @Column(name = "record_sequence", insertable = false, updatable = false, nullable = false)
    private Long recordSequence;
    @Column(nullable = false, unique = true, length = 80)
    private String reference;
    @Column(name = "id_number_hash", nullable = false, length = 64)
    private String idNumberHash;
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;
    @Enumerated(EnumType.STRING) @Column(name = "identity_status", nullable = false, length = 32)
    private VerificationStatus identityStatus;
    @Enumerated(EnumType.STRING) @Column(name = "location_status", nullable = false, length = 32)
    private VerificationStatus locationStatus;
    @Embedded
    private RegisteredLocation location;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    protected SimulationVoterRecord() { }
    public SimulationVoterRecord(String reference, String idNumberHash, String fullName, VerificationStatus identityStatus,
            VerificationStatus locationStatus, RegisteredLocation location, String reason, Instant recordedAt) {
        this.reference = reference; this.idNumberHash = idNumberHash; this.fullName = fullName;
        this.identityStatus = identityStatus; this.locationStatus = locationStatus; this.location = location;
        this.reason = reason; this.recordedAt = recordedAt;
    }
    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public String getIdNumberHash() { return idNumberHash; }
    public String getFullName() { return fullName; }
    public VerificationStatus getIdentityStatus() { return identityStatus; }
    public VerificationStatus getLocationStatus() { return locationStatus; }
    public RegisteredLocation getLocation() { return location == null ? RegisteredLocation.nationalOnly() : location; }
    public String getReason() { return reason; }
    public Instant getRecordedAt() { return recordedAt; }
}
