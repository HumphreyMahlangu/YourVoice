package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "election_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_election_registrations_profile_election",
                columnNames = {"voter_profile_id", "election_id"}
        )
)
public class ElectionRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_profile_id", nullable = false)
    private VoterProfile voterProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voting_district_id")
    private VotingDistrict votingDistrict;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegistrationStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "identity_verified", nullable = false)
    private boolean identityVerified;
    @Column(name = "location_verified", nullable = false)
    private boolean locationVerified;
    @Column(name = "source_reference", length = 80)
    private String sourceReference;
    @Column(name = "registered_province_id")
    private UUID registeredProvinceId;
    @Column(name = "registered_municipality_id")
    private UUID registeredMunicipalityId;
    @Column(name = "registered_province", length = 80)
    private String registeredProvince;
    @Column(name = "registered_municipality", length = 160)
    private String registeredMunicipality;
    @Column(name = "registered_ward_number")
    private Integer registeredWardNumber;

    public boolean isIdentityVerified() { return identityVerified; }
    public boolean isLocationVerified() { return locationVerified; }
    public String getSourceReference() { return sourceReference; }
    public UUID getRegisteredProvinceId() { return registeredProvinceId; }
    public UUID getRegisteredMunicipalityId() { return registeredMunicipalityId; }
    public String getRegisteredProvince() { return registeredProvince; }
    public String getRegisteredMunicipality() { return registeredMunicipality; }
    public Integer getRegisteredWardNumber() { return registeredWardNumber; }

    /** One-time explicit confirmation; later corrections cannot rewrite an election's location. */
    public void confirmVerification(VoterVerification verification, RegisteredLocation allowedLocation) {
        if (identityVerified) throw new IllegalStateException("Verified registration location is immutable");
        if (verification.getIdentityStatus() != VerificationStatus.VERIFIED) throw new IllegalStateException("Verified identity required");
        this.identityVerified = true;
        this.sourceReference = verification.getSourceReference();
        this.locationVerified = verification.getLocationStatus() == VerificationStatus.VERIFIED && allowedLocation.getProvince() != null;
        if (this.locationVerified) {
            this.registeredProvinceId = allowedLocation.getProvince().getId();
            this.registeredProvince = allowedLocation.provinceName();
            this.registeredMunicipalityId = allowedLocation.getMunicipality() == null ? null : allowedLocation.getMunicipality().getId();
            this.registeredMunicipality = allowedLocation.municipalityName();
            this.registeredWardNumber = allowedLocation.wardNumber();
        }
    }

    protected ElectionRegistration() {
    }

    public ElectionRegistration(
            VoterProfile voterProfile,
            Election election,
            VotingDistrict votingDistrict,
            RegistrationStatus status,
            Instant registeredAt
    ) {
        this.voterProfile = voterProfile;
        this.election = election;
        this.votingDistrict = votingDistrict;
        this.status = status;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public VoterProfile getVoterProfile() {
        return voterProfile;
    }

    public Election getElection() {
        return election;
    }

    public VotingDistrict getVotingDistrict() {
        return votingDistrict;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
