package io.github.humphreymahlangu.yourvoice.dto;

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
    /** Prevent framework debug tracing from serialising sensitive record components into logs. */
    @Override
    public String toString() { return "RefreshTokenRequest[redacted]"; }
}
