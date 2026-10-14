package io.github.humphreymahlangu.votetrust.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for JWT authentication")
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(description = "Account email address", example = "voter@example.com")
        String email,

        @NotBlank
        @Size(max = 128)
        @Schema(description = "Account password", example = "StrongPass123", format = "password")
        String password
) {
}
