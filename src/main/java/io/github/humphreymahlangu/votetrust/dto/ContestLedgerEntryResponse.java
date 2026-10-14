package io.github.humphreymahlangu.votetrust.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Public ballot ledger entry. Ballots are shuffled before publication, so position says nothing about cast order. "
        + "currentHash = SHA-256 hex of \"votetrust-public-ledger-v1|contestId|contestOptionId|position|previousHash|nonce\".")
public record ContestLedgerEntryResponse(
        @Schema(description = "Position in the published (shuffled) ledger, starting at 0", example = "0")
        long position,

        @Schema(description = "Selected contest option identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID contestOptionId,

        @Schema(description = "Previous published entry hash, or the genesis hash for position 0")
        String previousHash,

        @Schema(description = "SHA-256 hash for this published entry")
        String currentHash,

        @Schema(description = "Per-entry nonce included in the hash calculation")
        String nonce
) {
}
