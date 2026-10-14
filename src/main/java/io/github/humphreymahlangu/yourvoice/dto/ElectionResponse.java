package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Election summary")
public record ElectionResponse(
        @Schema(description = "Election identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Election display name", example = "2026 Local Government Election")
        String name,

        @Schema(description = "Election type", example = "LOCAL_GOVERNMENT")
        String type,

        @Schema(description = "Election lifecycle status", example = "VOTING_OPEN")
        String status,

        @Schema(description = "UTC instant when registration opens", example = "2026-08-01T06:00:00Z")
        Instant registrationStartAt,

        @Schema(description = "UTC instant when registration closes", example = "2026-08-05T18:00:00Z")
        Instant registrationEndAt,

        @Schema(description = "UTC instant when voting opens", example = "2026-08-07T06:00:00Z")
        Instant votingStartAt,

        @Schema(description = "UTC instant when voting closes", example = "2026-08-07T19:00:00Z")
        Instant votingEndAt
) {

    public static ElectionResponse from(Election election) {
        return new ElectionResponse(
                election.getId(),
                election.getName(),
                election.getType().name(),
                election.getStatus().name(),
                election.getRegistrationStartAt(),
                election.getRegistrationEndAt(),
                election.getVotingStartAt(),
                election.getVotingEndAt()
        );
    }
}
