package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "JWT authentication response")
public record AuthResponse(
        @Schema(description = "Signed JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Token type for the Authorization header", example = "Bearer")
        String tokenType,

        @Schema(description = "UTC expiry timestamp for the access token", example = "2026-08-07T10:30:00Z")
        Instant expiresAt,

        @Schema(description = "Authenticated account identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID userId,

        @Schema(description = "Authenticated account email", example = "voter@example.com")
        String email,

        @Schema(description = "Account role", example = "VOTER")
        String role,

        @Schema(description = "Opaque refresh token. Exchange it at /api/v1/auth/refresh; each use rotates it.")
        String refreshToken,

        @Schema(description = "UTC expiry timestamp for the refresh token", example = "2026-08-07T22:15:00Z")
        Instant refreshTokenExpiresAt
) {
}
