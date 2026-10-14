package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin request for creating a South African voting district")
public record CreateVotingDistrictRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "must contain only letters, digits, and hyphens")
        @Schema(description = "Voting district code", example = "WC-CPT-12")
        String code,

        @NotBlank
        @Size(max = 160)
        @Schema(description = "Voting district display name", example = "Cape Town Ward 12")
        String name,

        @NotBlank
        @Size(max = 80)
        @Schema(description = "Province name", example = "Western Cape")
        String province,

        @NotBlank
        @Size(max = 160)
        @Schema(description = "Municipality name", example = "City of Cape Town")
        String municipality,

        @NotNull
        @Min(1)
        @Max(9999)
        @Schema(description = "Municipal ward number", example = "12")
        Integer wardNumber
) {
}
