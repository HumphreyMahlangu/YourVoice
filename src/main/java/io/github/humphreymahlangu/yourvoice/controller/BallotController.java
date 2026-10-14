package io.github.humphreymahlangu.yourvoice.controller;

import io.github.humphreymahlangu.yourvoice.dto.ApiErrorResponse;
import io.github.humphreymahlangu.yourvoice.dto.BallotCastRequest;
import io.github.humphreymahlangu.yourvoice.dto.BallotCastResponse;
import io.github.humphreymahlangu.yourvoice.service.VotingService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ballots")
@Tag(name = "Ballots", description = "Anonymous ballot submission")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Ballot signature is invalid",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Contest or ballot option was not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Voting is closed or the credential was already used",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "429",
                description = "Ballot submission rate limit exceeded",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class BallotController {

    private final VotingService votingService;

    public BallotController(VotingService votingService) {
        this.votingService = votingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cast an anonymous ballot",
            description = "Accepts a ballot token, its unblinded signature, and the selected option. No JWT is sent, and the server cannot link the token to the voter it signed for. Each token can be spent once. The response intentionally omits ledger hashes, indexes, and identifiers that could become a vote receipt."
    )
    public BallotCastResponse castBallot(@Valid @RequestBody BallotCastRequest request) {
        return votingService.castBallot(request);
    }
}
