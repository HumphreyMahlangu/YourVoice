package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Final tally for a contest option")
public record ContestOptionResultResponse(
        @Schema(description = "Contest option identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID contestOptionId,

        @Schema(description = "Option display name", example = "Example Party")
        String name,

        @Schema(description = "Valid vote option type", example = "PARTY")
        ContestOptionType optionType,

        @Schema(description = "Display ordering on the ballot", example = "1")
        Integer displayOrder,

        @Schema(description = "Votes counted for this option", example = "350")
        long voteCount,

        @Schema(description = "Share of valid votes", example = "54.26")
        BigDecimal percentageOfValidVotes,

        @Schema(description = "Whether this option currently has the highest valid vote count", example = "true")
        boolean leading
) {
}
