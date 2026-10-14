package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Administrator account")
public record AdminAccountResponse(
        @Schema(description = "Account identifier")
        UUID id,

        @Schema(description = "Administrator email", example = "admin@example.com")
        String email,

        @Schema(description = "Whether the account can sign in", example = "true")
        boolean enabled,

        @Schema(description = "UTC creation time")
        Instant createdAt
) {

    public static AdminAccountResponse from(UserAccount account) {
        return new AdminAccountResponse(account.getId(), account.getEmail(), account.isEnabled(), account.getCreatedAt());
    }
}
