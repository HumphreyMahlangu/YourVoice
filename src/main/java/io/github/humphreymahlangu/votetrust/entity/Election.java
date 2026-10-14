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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "elections")
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ElectionType type;

    @Column(name = "registration_start_at", nullable = false)
    private Instant registrationStartAt;

    @Column(name = "registration_end_at", nullable = false)
    private Instant registrationEndAt;

    @Column(name = "voting_start_at", nullable = false)
    private Instant votingStartAt;

    @Column(name = "voting_end_at", nullable = false)
    private Instant votingEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ElectionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Election() {
    }

    public Election(
            String name,
            ElectionType type,
            Instant registrationStartAt,
            Instant registrationEndAt,
            Instant votingStartAt,
            Instant votingEndAt,
            ElectionStatus status
    ) {
        this.name = name;
        this.type = type;
        this.registrationStartAt = registrationStartAt;
        this.registrationEndAt = registrationEndAt;
        this.votingStartAt = votingStartAt;
        this.votingEndAt = votingEndAt;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ElectionType getType() {
        return type;
    }

    public Instant getRegistrationStartAt() {
        return registrationStartAt;
    }

    public Instant getRegistrationEndAt() {
        return registrationEndAt;
    }

    public Instant getVotingStartAt() {
        return votingStartAt;
    }

    public Instant getVotingEndAt() {
        return votingEndAt;
    }

    public ElectionStatus getStatus() {
        return status;
    }

    public void transitionTo(ElectionStatus status) {
        this.status = status;
    }

    /** Renames and reschedules a DRAFT election. Callers validate the windows and the status. */
    public void updateDetails(
            String name,
            Instant registrationStartAt,
            Instant registrationEndAt,
            Instant votingStartAt,
            Instant votingEndAt
    ) {
        this.name = name;
        this.registrationStartAt = registrationStartAt;
        this.registrationEndAt = registrationEndAt;
        this.votingStartAt = votingStartAt;
        this.votingEndAt = votingEndAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
