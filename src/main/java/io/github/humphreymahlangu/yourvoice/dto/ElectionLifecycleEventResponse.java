package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Auditable automatic or administrator-triggered election lifecycle event")
public record ElectionLifecycleEventResponse(
        @Schema(description = "Lifecycle event identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Election identifier", example = "22222222-2222-2222-2222-222222222222")
        UUID electionId,

        @Schema(description = "Election status before the attempted transition", example = "REGISTRATION_CLOSED")
        String previousStatus,

        @Schema(description = "Requested new election status", example = "VOTING_OPEN")
        String newStatus,

        @Schema(description = "Transition initiator", example = "AUTOMATIC")
        String trigger,

        @Schema(description = "Transition outcome", example = "SUCCESS")
        String outcome,

        @Schema(description = "Administrator account identifier for emergency actions")
        UUID actorUserId,

        @Schema(description = "Administrator email for emergency actions", example = "admin@example.com")
        String actorEmail,

        @Schema(description = "Lifecycle event detail", example = "Voting opened automatically and 2 contests were opened")
        String detail,

        @Schema(description = "UTC timestamp when the event occurred", example = "2026-08-09T10:00:00Z")
        Instant occurredAt
) {

    public static ElectionLifecycleEventResponse from(ElectionLifecycleEvent event) {
        return new ElectionLifecycleEventResponse(
                event.getId(),
                event.getElectionId(),
                event.getPreviousStatus().name(),
                event.getNewStatus() == null ? null : event.getNewStatus().name(),
                event.getTrigger().name(),
                event.getOutcome().name(),
                event.getActorUserId(),
                event.getActorEmail(),
                event.getDetail(),
                event.getOccurredAt()
        );
    }
}
