package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "Admin request for creating an election in DRAFT status")
public record CreateElectionRequest(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Election display name", example = "2026 Local Government Election")
        String name,

        @NotNull
        @Schema(description = "Election type", example = "LOCAL_GOVERNMENT")
        ElectionType type,

        @NotNull
        @Schema(description = "UTC instant when voter registration opens", example = "2026-08-01T06:00:00Z")
        Instant registrationStartAt,

        @NotNull
        @Schema(description = "UTC instant when voter registration closes", example = "2026-08-05T18:00:00Z")
        Instant registrationEndAt,

        @NotNull
        @Schema(description = "UTC instant when voting opens", example = "2026-08-07T06:00:00Z")
        Instant votingStartAt,

        @NotNull
        @Schema(description = "UTC instant when voting closes", example = "2026-08-07T19:00:00Z")
        Instant votingEndAt
) {
}
