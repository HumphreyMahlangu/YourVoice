package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Authenticated voter request for election registration")
public record ElectionRegistrationRequest(
        @Schema(
                description = "Optional confirmation of the voting district on the voter profile. The registration always uses the profile district; a different value is rejected.",
                example = "11111111-1111-1111-1111-111111111111"
        )
        UUID votingDistrictId
) {
}
