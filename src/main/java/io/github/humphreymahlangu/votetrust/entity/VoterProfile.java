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
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "voter_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_voter_profiles_user_account", columnNames = "user_account_id"),
                @UniqueConstraint(name = "uk_voter_profiles_id_number_hash", columnNames = "id_number_hash")
        }
)
public class VoterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(name = "id_number_hash", nullable = false, length = 64)
    private String idNumberHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_document_type", length = 40)
    private IdDocumentType idDocumentType;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voting_district_id", nullable = false)
    private VotingDistrict votingDistrict;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoterProfile() {
    }

    public VoterProfile(
            UserAccount userAccount,
            String fullName,
            String idNumberHash,
            IdDocumentType idDocumentType,
            LocalDate dateOfBirth,
            VotingDistrict votingDistrict
    ) {
        this.userAccount = userAccount;
        this.fullName = fullName;
        this.idNumberHash = idNumberHash;
        this.idDocumentType = idDocumentType;
        this.dateOfBirth = dateOfBirth;
        this.votingDistrict = votingDistrict;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIdNumberHash() {
        return idNumberHash;
    }

    public IdDocumentType getIdDocumentType() {
        return idDocumentType;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public VotingDistrict getVotingDistrict() {
        return votingDistrict;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
