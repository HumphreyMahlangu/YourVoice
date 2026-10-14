package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.ContestAuditResponse;
import io.github.humphreymahlangu.votetrust.dto.ContestLedgerEntryResponse;
import io.github.humphreymahlangu.votetrust.dto.ContestResultResponse;
import io.github.humphreymahlangu.votetrust.service.TallyAuditService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/elections/{electionId}/contests/{contestId}")
@Tag(name = "Results and Ledger Audit", description = "Final results, hash-chain verification, and public anonymized ledger APIs")
@ApiResponses({
        @ApiResponse(
                responseCode = "404",
                description = "Election or contest was not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Results or ledger data are unavailable until voting is closed and the election is completed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class ContestResultController {

    private final TallyAuditService tallyAuditService;

    public ContestResultController(TallyAuditService tallyAuditService) {
        this.tallyAuditService = tallyAuditService;
    }

    @GetMapping("/results")
    @Operation(summary = "Get final contest results", description = "Returns final tallies only after the election is completed and the contest is closed. Explicit blank and spoilt ballots are excluded from valid vote totals.")
    public ContestResultResponse getContestResults(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId
    ) {
        return tallyAuditService.getContestResult(electionId, contestId);
    }

    @GetMapping("/audit")
    @Operation(summary = "Verify the contest ballot hash chain", description = "Recomputes the SHA-256 hash chain from the public ledger and compares it with stored ledger state.")
    public ContestAuditResponse verifyContestLedger(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId
    ) {
        return tallyAuditService.verifyContestLedger(electionId, contestId);
    }

    @GetMapping("/ledger")
    @Operation(summary = "List public contest ledger entries", description = "Returns anonymized ledger entries for independent recounting without voter identity or exact cast timestamps.")
    public List<ContestLedgerEntryResponse> listContestLedger(
            @PathVariable UUID electionId,
            @PathVariable UUID contestId
    ) {
        return tallyAuditService.listContestLedger(electionId, contestId);
    }
}
