package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request for a blind signature over a ballot token the client has blinded")
public record VotingCredentialRequest(
        @NotBlank
        @Size(max = 700)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must be unpadded base64url")
        @Schema(
                description = "H(ballotToken) * r^e mod n as unpadded base64url big-endian bytes. The server never sees the ballot token or r.",
                example = "mF3k..."
        )
        String blindedToken
) {
}
