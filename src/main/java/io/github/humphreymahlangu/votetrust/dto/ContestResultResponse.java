package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Final contest results after voting is closed and the election is completed")
public record ContestResultResponse(
        @Schema(description = "Election identifier", example = "11111111-1111-1111-1111-111111111111")
        UUID electionId,

        @Schema(description = "Contest identifier", example = "22222222-2222-2222-2222-222222222222")
        UUID contestId,

        @Schema(description = "Contest display name", example = "Ward 12 Councillor")
        String contestName,

        @Schema(description = "Contest type", example = "MUNICIPAL_WARD")
        ContestType contestType,

        @Schema(description = "Number of registered voters eligible for this contest", example = "1000")
        long registeredVoterCount,

        @Schema(description = "Number of accepted ballots cast, including valid, blank, and spoilt ballots", example = "645")
        long ballotsCast,

        @Schema(description = "Number of valid votes counted toward party or independent candidate options", example = "645")
        long validVotes,

        @Schema(description = "Number of explicit blank ballots", example = "0")
        long blankBallots,

        @Schema(description = "Number of explicit spoilt ballots", example = "0")
        long spoiltBallots,

        @Schema(description = "Turnout as a percentage of registered voters eligible for this contest", example = "64.50")
        BigDecimal turnoutPercentage,

        @Schema(description = "Head hash of the published ledger, verifiable by anyone from the public ledger entries")
        String ledgerHeadHash,

        @Schema(description = "UTC timestamp when results were generated", example = "2026-08-07T20:15:30Z")
        Instant generatedAt,

        @Schema(description = "Per-option tally rows for valid vote options only")
        List<ContestOptionResultResponse> options
) {
}
