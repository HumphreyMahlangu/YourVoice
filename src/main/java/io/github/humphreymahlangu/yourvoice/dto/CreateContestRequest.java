package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin request for creating a contest within an election")
public record CreateContestRequest(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Contest display name", example = "Ward 12 Councillor")
        String name,

        @NotNull
        @Schema(description = "Contest type and geographic rule set", example = "MUNICIPAL_WARD")
        ContestType type,

        @NotNull
        @Min(1)
        @Max(9999)
        @Schema(description = "Display ordering within the election", example = "1")
        Integer displayOrder,

        @Size(max = 80)
        @Schema(description = "Province scope required for provincial and municipal contests", example = "Western Cape")
        String scopeProvince,

        @Size(max = 160)
        @Schema(description = "Municipality scope required for municipal contests", example = "City of Cape Town")
        String scopeMunicipality,

        @Min(1)
        @Max(9999)
        @Schema(description = "Ward number required for municipal ward contests", example = "12")
        Integer scopeWardNumber
) {
}
