package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin request for transitioning contest lifecycle status")
public record ContestStatusUpdateRequest(
        @NotNull
        @Schema(description = "Next contest status", example = "OPEN")
        ContestStatus status
) {
}
