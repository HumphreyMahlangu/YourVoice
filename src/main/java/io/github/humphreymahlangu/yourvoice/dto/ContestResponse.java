package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Contest and ballot option summary")
public record ContestResponse(
        @Schema(description = "Contest identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Election identifier", example = "22222222-2222-2222-2222-222222222222")
        UUID electionId,

        @Schema(description = "Contest display name", example = "Ward 12 Councillor")
        String name,

        @Schema(description = "Contest type", example = "MUNICIPAL_WARD")
        String type,

        @Schema(description = "Contest lifecycle status", example = "OPEN")
        String status,

        @Schema(description = "Display ordering within the election", example = "1")
        Integer displayOrder,

        @Schema(description = "Province scope for eligibility", example = "Western Cape")
        String scopeProvince,

        @Schema(description = "Municipality scope for eligibility", example = "City of Cape Town")
        String scopeMunicipality,

        @Schema(description = "Ward scope for eligibility", example = "12")
        Integer scopeWardNumber,

        @Schema(description = "Contest ballot options")
        List<ContestOptionResponse> options
) {

    /** Builds the response from a contest and its options, which must already be in display order. */
    public static ContestResponse from(Contest contest, List<ContestOption> options) {
        return new ContestResponse(
                contest.getId(),
                contest.getElection().getId(),
                contest.getName(),
                contest.getType().name(),
                contest.getStatus().name(),
                contest.getDisplayOrder(),
                contest.getScopeProvince(),
                contest.getScopeMunicipality(),
                contest.getScopeWardNumber(),
                options.stream().map(ContestOptionResponse::from).toList()
        );
    }
}
