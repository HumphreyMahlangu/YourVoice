package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin request for creating a ballot option")
public record CreateContestOptionRequest(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Ballot option or party name", example = "Example Party")
        String name,

        @NotNull
        @Schema(
                description = "Ballot option type. PARTY and INDEPENDENT_CANDIDATE count as valid votes; BLANK_BALLOT and SPOILT_BALLOT are explicit non-valid ballot choices.",
                example = "PARTY"
        )
        ContestOptionType optionType,

        @NotNull
        @Min(1)
        @Max(9999)
        @Schema(description = "Display ordering on the ballot", example = "1")
        Integer displayOrder
) {
}
