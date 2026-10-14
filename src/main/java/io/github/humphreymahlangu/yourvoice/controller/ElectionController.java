package io.github.humphreymahlangu.votetrust.controller;

import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import io.github.humphreymahlangu.votetrust.dto.ElectionRegistrationRequest;
import io.github.humphreymahlangu.votetrust.dto.ElectionRegistrationResponse;
import io.github.humphreymahlangu.votetrust.dto.ElectionResponse;
import io.github.humphreymahlangu.votetrust.security.UserPrincipal;
import io.github.humphreymahlangu.votetrust.service.ElectionService;
import io.github.humphreymahlangu.votetrust.service.VoterRegistrationService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/elections")
@Tag(name = "Elections", description = "Public election browsing and authenticated voter registration")
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Request validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Authentication is required for voter registration",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Election or voting district was not found",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Registration period is closed or the voter is already registered",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        ),
        @ApiResponse(
                responseCode = "422",
                description = "South African ID or voter eligibility validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
        )
})
public class ElectionController {

    private final ElectionService electionService;
    private final VoterRegistrationService voterRegistrationService;

    public ElectionController(ElectionService electionService, VoterRegistrationService voterRegistrationService) {
        this.electionService = electionService;
        this.voterRegistrationService = voterRegistrationService;
    }

    @GetMapping
    @Operation(summary = "List elections", description = "Returns elections with lifecycle status and registration/voting windows.")
    public ResponseEntity<List<ElectionResponse>> listElections(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = PageRequestParameters.DEFAULT_PAGE) @Min(0) int page,
            @Parameter(description = "Page size (1-500). The total is returned in the X-Total-Count header.", example = "200")
            @RequestParam(defaultValue = PageRequestParameters.DEFAULT_SIZE) @Min(1) @Max(PageRequestParameters.MAX_SIZE) int size
    ) {
        return PageRequestParameters.toResponse(electionService.listElections(PageRequest.of(page, size)));
    }

    @GetMapping("/{electionId}")
    @Operation(summary = "Get election details", description = "Returns a single election by identifier.")
    public ElectionResponse getElection(@PathVariable UUID electionId) {
        return electionService.getElection(electionId);
    }

    @PostMapping("/{electionId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register authenticated voter for an election",
            description = "Registers the authenticated voter during the registration window in the voting district on their voter profile. The request body is optional; a votingDistrictId that differs from the profile district is rejected with 422.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ElectionRegistrationResponse registerForElection(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID electionId,
            @Valid @RequestBody(required = false) ElectionRegistrationRequest request
    ) {
        return voterRegistrationService.registerForElection(principal.id(), electionId, request);
    }
}
