package io.github.humphreymahlangu.votetrust.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import io.github.humphreymahlangu.votetrust.entity.IdDocumentType;
import java.util.UUID;

@Schema(description = "Request body for creating a voter platform account")
public record RegisterRequest(
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Voter full legal name", example = "Thandi Nomsa Mokoena")
        String fullName,

        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(description = "Voter email address used for authentication", example = "voter@example.com")
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "must contain at least one uppercase letter, one lowercase letter, and one digit"
        )
        @Schema(description = "Strong account password", example = "StrongPass123", format = "password")
        String password,

        @NotBlank
        @Pattern(regexp = "\\d{13}", message = "must be a 13-digit South African ID number")
        @Schema(description = "13-digit South African ID number. The API stores only a peppered hash.", example = "8001015009087")
        String southAfricanIdNumber,

        @NotNull
        @Schema(description = "Identity document type supplied during voter onboarding", example = "SMART_ID_CARD")
        IdDocumentType idDocumentType,

        @NotNull
        @Schema(description = "Voter's current voting district", example = "11111111-1111-1111-1111-111111111111")
        UUID votingDistrictId
) {
}
