package io.github.humphreymahlangu.yourvoice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Public RSA key used to blind ballot tokens for a contest. Every voter in the contest must see the same key.")
public record BallotKeyResponse(
        @Schema(description = "Contest identifier", example = "22222222-2222-2222-2222-222222222222")
        UUID contestId,

        @Schema(description = "RSA modulus n as unpadded base64url big-endian bytes")
        String modulus,

        @Schema(description = "RSA public exponent e as unpadded base64url big-endian bytes", example = "AQAB")
        String publicExponent,

        @Schema(description = "SHA-256 of the modulus bytes, for comparing keys across voters")
        String keyFingerprint,

        @Schema(
                description = "Domain for the full-domain hash: H(t) = MGF1-SHA256(\"<tokenDomain>|<contestId>|<ballotToken>\", modulusBytes + 16) mod n",
                example = "votetrust-ballot-token-v1"
        )
        String tokenDomain
) {
}
