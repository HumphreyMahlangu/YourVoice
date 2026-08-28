package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Authenticated voter request for election registration")
public record ElectionRegistrationRequest(
        @NotNull
        @Schema(description = "Confirmed voting district for this election", example = "11111111-1111-1111-1111-111111111111")
        UUID votingDistrictId
) {
}
