package io.github.humphreymahlangu.yourvoice.controller;

import io.github.humphreymahlangu.yourvoice.dto.ApiErrorResponse;
import io.github.humphreymahlangu.yourvoice.dto.BallotKeyResponse;
import io.github.humphreymahlangu.yourvoice.dto.ContestResponse;
import io.github.humphreymahlangu.yourvoice.dto.VotingCredentialRequest;
import io.github.humphreymahlangu.yourvoice.dto.VotingCredentialResponse;
import io.github.humphreymahlangu.yourvoice.security.UserPrincipal;
import io.github.humphreymahlangu.yourvoice.service.ContestService;
import io.github.humphreymahlangu.yourvoice.service.VotingService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/elections/{electionId}/contests")
@Tag(name = "Contests", description = "Election contest browsing and anonymous credential issuance")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed or the blinded token is not valid for the contest key",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication is required for credential issuance",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "403",
                description = "Authenticated account cannot access the requested operation",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Election or contest was not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Voting is closed or a credential was already issued",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "422",
                description = "Voter is not eligible for this contest",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "429",
                description = "Credential issuance rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class ContestController {

    private final ContestService contestService;
    private final VotingService votingService;

    public ContestController(ContestService contestService, VotingService votingService) {
        this.contestService = contestService;
        this.votingService = votingService;
    }

    @GetMapping
    @Operation(summary = "List contests and ballot options", description = "Returns contests configured for a public election, including geographic scope fields used for eligibility. Draft and cancelled elections require an administrator bearer token.")
    public List<ContestResponse> listContests(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID electionId
    ) {
        return contestService.listContests(electionId, principal != null && "ADMIN".equals(principal.role()));
    }

    @PostMapping("/{contestId}/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Blind-sign a ballot token",
            description = "After age, registration, voting-window, and geographic eligibility checks, signs the client's blinded ballot token. Each voter gets one signature per contest; repeating the request with the same blinded token returns the same signature. Because the token is blinded, the server cannot link the resulting ballot to the voter.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public VotingCredentialResponse issueVotingCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID electionId,
            @PathVariable UUID contestId,
            @Valid @RequestBody VotingCredentialRequest request
    ) {
        return votingService.issueVotingCredential(principal.id(), electionId, contestId, request);
    }

    @GetMapping("/{contestId}/ballot-key")
    @Operation(
            summary = "Get the contest ballot key",
            description = "Returns the public RSA key voters use to blind their ballot token before requesting a credential. Available once voting opens. Every voter must receive the same key; compare keyFingerprint to check."
    )
    public BallotKeyResponse getBallotKey(@PathVariable UUID electionId, @PathVariable UUID contestId) {
        return votingService.getBallotKey(electionId, contestId);
    }
}
