package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Anonymous ballot submission authorised by an unblinded ballot signature")
public record BallotCastRequest(
        @NotNull
        @Schema(description = "Contest being voted in", example = "11111111-1111-1111-1111-111111111111")
        UUID contestId,

        @NotNull
        @Schema(description = "Selected contest option", example = "22222222-2222-2222-2222-222222222222")
        UUID contestOptionId,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "must be 32 random bytes as unpadded base64url")
        @Schema(description = "Random ballot token chosen by the client. Spent once the ballot is accepted.", example = "q8Xb0...")
        String ballotToken,

        @NotBlank
        @Size(max = 700)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must be unpadded base64url")
        @Schema(description = "Unblinded signature H(ballotToken)^d mod n as unpadded base64url. Not a JWT.")
        String signature
) {
}
