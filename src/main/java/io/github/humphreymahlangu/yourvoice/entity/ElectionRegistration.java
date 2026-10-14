package io.github.humphreymahlangu.votetrust.entity;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voting_district_id", nullable = false)
    private VotingDistrict votingDistrict;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegistrationStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

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
