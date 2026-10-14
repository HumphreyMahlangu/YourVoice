package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin request for transitioning election lifecycle status")
public record ElectionStatusUpdateRequest(
        @NotNull
        @Schema(description = "Next election status", example = "REGISTRATION_OPEN")
        ElectionStatus status
) {
}
