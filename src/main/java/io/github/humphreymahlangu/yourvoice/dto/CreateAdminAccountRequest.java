package io.github.humphreymahlangu.yourvoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "New administrator account created by an existing administrator")
public record CreateAdminAccountRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        @Schema(description = "Administrator email", example = "second.admin@example.com")
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "must contain at least one uppercase letter, one lowercase letter, and one digit"
        )
        @Schema(description = "Initial password, shared with the new administrator out of band", example = "VeryStrongPassword1")
        String password
) {
}
