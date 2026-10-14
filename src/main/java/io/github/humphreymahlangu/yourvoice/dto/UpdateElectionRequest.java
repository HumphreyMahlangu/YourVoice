package io.github.humphreymahlangu.yourvoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "Replacement name and schedule for a DRAFT election. The election type cannot be changed.")
public record UpdateElectionRequest(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Election name", example = "2026 Local Government Election")
        String name,

        @NotNull
        @Schema(description = "UTC registration opening time", example = "2026-07-01T00:00:00Z")
        Instant registrationStartAt,

        @NotNull
        @Schema(description = "UTC registration closing time; must be in the future", example = "2026-08-01T00:00:00Z")
        Instant registrationEndAt,

        @NotNull
        @Schema(description = "UTC voting opening time", example = "2026-08-07T07:00:00Z")
        Instant votingStartAt,

        @NotNull
        @Schema(description = "UTC voting closing time", example = "2026-08-07T21:00:00Z")
        Instant votingEndAt
) {
}
