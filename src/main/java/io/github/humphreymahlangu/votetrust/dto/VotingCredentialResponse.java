package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Blind signature over the client's blinded ballot token")
public record VotingCredentialResponse(
        @Schema(description = "Election the credential belongs to", example = "11111111-1111-1111-1111-111111111111")
        UUID electionId,

        @Schema(description = "Contest the credential may be used for", example = "22222222-2222-2222-2222-222222222222")
        UUID contestId,

        @Schema(description = "blindedToken^d mod n as unpadded base64url. Unblind it with r^-1 to get the ballot signature.", example = "Qk9x...")
        String blindSignature,

        @Schema(description = "SHA-256 fingerprint of the contest ballot key that produced the signature")
        String keyFingerprint,

        @Schema(description = "UTC time after which ballots for this contest are no longer accepted", example = "2026-08-07T20:00:00Z")
        Instant expiresAt
) {
}
