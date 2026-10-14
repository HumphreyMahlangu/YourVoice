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
@Table(name = "election_lifecycle_events")
public class ElectionLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_sequence", nullable = false, insertable = false, updatable = false)
    private Long eventSequence;

    @Column(name = "election_id", nullable = false)
    private UUID electionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 32)
    private ElectionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 32)
    private ElectionStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ElectionLifecycleTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ElectionLifecycleOutcome outcome;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(nullable = false, length = 512)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ElectionLifecycleEvent() {
    }

    public ElectionLifecycleEvent(
            UUID electionId,
            ElectionStatus previousStatus,
            ElectionStatus newStatus,
            ElectionLifecycleTrigger trigger,
            ElectionLifecycleOutcome outcome,
            UUID actorUserId,
            String actorEmail,
            String detail,
            Instant occurredAt
    ) {
        this.electionId = electionId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.trigger = trigger;
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getElectionId() {
        return electionId;
    }

    public Long getEventSequence() {
        return eventSequence;
    }

    public ElectionStatus getPreviousStatus() {
        return previousStatus;
    }

    public ElectionStatus getNewStatus() {
        return newStatus;
    }

    public ElectionLifecycleTrigger getTrigger() {
        return trigger;
    }

    public ElectionLifecycleOutcome getOutcome() {
        return outcome;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
