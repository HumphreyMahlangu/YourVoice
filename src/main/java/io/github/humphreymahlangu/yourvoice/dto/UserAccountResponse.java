package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Authenticated user account summary")
public record UserAccountResponse(
        @Schema(description = "Account identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID id,

        @Schema(description = "Account email address", example = "voter@example.com")
        String email,

        @Schema(description = "Account role", example = "VOTER")
        String role,

        @Schema(description = "Whether the account may authenticate", example = "true")
        boolean enabled
) {
}
