package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Refresh token to exchange or revoke")
public record RefreshTokenRequest(
        @NotBlank
        @Size(max = 128)
        @Schema(description = "Refresh token from the latest AuthResponse")
        String refreshToken
) {
}
