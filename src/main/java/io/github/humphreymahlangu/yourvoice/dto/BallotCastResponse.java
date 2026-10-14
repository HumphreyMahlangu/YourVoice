package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Receipt-resistant ballot acceptance response")
public record BallotCastResponse(
        @Schema(description = "Contest that accepted the ballot", example = "11111111-1111-1111-1111-111111111111")
        UUID contestId,

        @Schema(description = "Whether the ballot was accepted", example = "true")
        boolean accepted,

        @Schema(description = "Short confirmation message without receipt-like ledger data", example = "Ballot accepted")
        String message
) {
}
